package com.livelyvillagers.dev.client;

import com.livelyvillagers.dev.FilmState;
import com.livelyvillagers.LivelyVillagers;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dev-only film tools: flies the camera along the director's path and records clips. Video is read
 * back from the main framebuffer every rendered frame and piped to ffmpeg (NVENC) at a fixed 30 fps
 * (frames are duplicated or dropped to match wall-clock time); audio is one continuous recording
 * of this process's own Pulse/PipeWire stream, cut per clip afterwards from clips.jsonl.
 */
public final class FilmClient {
	private static final int FPS = 30;
	private static final Path OUT = Path.of(System.getProperty("livelyvillagers.film.out", "film"));

	private static Process ffmpeg;
	private static Process audio;
	private static long audioStartNanos;
	private static String clip;
	private static long clipStartNanos;
	private static long framesWritten;
	private static int width;
	private static int height;
	private static BlockingQueue<ByteBuffer> free;
	private static BlockingQueue<ByteBuffer> full;
	private static Thread writer;
	private static ByteBuffer last;
	/** The slot whose pose the current render used, or -1 if it used "now". */
	private static long poseSlot = -1;

	static void install() {
		try {
			Files.createDirectories(OUT);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		ClientTickEvents.END_CLIENT_TICK.register(FilmClient::driveCamera);
	}

	// ------------------------------------------------------------------ camera

	private static void driveCamera(Minecraft mc) {
		if (FilmState.finished && clip == null) {
			FilmState.finished = false;
			stopAudio();
			LivelyVillagers.LOGGER.info("FILM client done");
			mc.stop();
			return;
		}
		LocalPlayer player = mc.player;
		FilmState.CameraPath path = FilmState.camera;
		if (player == null || path == null) {
			return;
		}
		// Pose for the end of this tick; the renderer interpolates from the previous tick's pose.
		double t = Math.max(0, (System.nanoTime() - FilmState.cameraStartNanos) / 1e9 + 0.05);
		FilmState.Pose p = path.at(t);
		player.getAbilities().flying = true;
		player.setDeltaMovement(Vec3.ZERO);
		player.setPos(p.x(), p.y() - player.getEyeHeight(), p.z());
		player.setYRot(p.yaw());
		player.setXRot(p.pitch());
		player.setYHeadRot(p.yaw());
	}

	/**
	 * The camera pose for the frame about to be rendered. While recording, a frame that will be
	 * captured gets the pose for its exact 1/30 s slot, so motion is perfectly even in the video no
	 * matter how irregular the real frame times are; otherwise the pose for "now".
	 */
	public static FilmState.Pose framePose() {
		FilmState.CameraPath path = FilmState.camera;
		if (!FilmState.active || path == null) {
			return null;
		}
		long when = System.nanoTime();
		poseSlot = -1;
		if (clip != null) {
			long slot = clipStartNanos + framesWritten * 1_000_000_000L / FPS;
			if (when >= slot) {
				when = slot;
				poseSlot = framesWritten;
			}
		}
		return path.at(Math.max(0, (when - FilmState.cameraStartNanos) / 1e9));
	}

	// ------------------------------------------------------------------ recording

	/** Called at the end of GameRenderer.render, when the main framebuffer holds the finished frame. */
	public static void onFrameRendered() {
		if (!FilmState.active) {
			return;
		}
		String wanted = FilmState.recording;
		if (wanted != null && !wanted.equals(clip)) {
			stopClip();
			startClip(wanted);
		} else if (wanted == null && clip != null) {
			stopClip();
		}
		if (clip == null) {
			return;
		}
		// Only frames rendered with their slot's exact pose are captured.
		if (poseSlot != framesWritten) {
			return;
		}
		// This render used the pose for slot `framesWritten` (see framePose), so it fills exactly that
		// slot. If rendering ever falls behind, the remaining slots are filled by the next renders,
		// each with its own pose, so the motion stays even (the clip just ends a little later).
		enqueue(grab());
		framesWritten++;
	}

	private static ByteBuffer grab() {
		var target = Minecraft.getInstance().getMainRenderTarget();
		ByteBuffer buf;
		try {
			buf = free.take();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		buf.clear();
		RenderSystem.bindTexture(target.getColorTextureId());
		GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
		RenderSystem.bindTexture(0);
		last = buf;
		return buf;
	}

	private static void enqueue(ByteBuffer frame) {
		try {
			// The same buffer may be queued more than once (a duplicated frame); the writer returns it once.
			full.put(frame);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private static void startClip(String name) {
		var target = Minecraft.getInstance().getMainRenderTarget();
		width = target.width;
		height = target.height;
		if (audio == null) {
			startAudio();
		}
		Path file = OUT.resolve(name + ".mp4").toAbsolutePath();
		try {
			ffmpeg = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error",
				"-f", "rawvideo", "-pix_fmt", "rgba", "-s", width + "x" + height, "-r", String.valueOf(FPS), "-i", "-",
				"-vf", "vflip", "-c:v", "h264_nvenc", "-preset", "p6", "-rc", "vbr", "-cq", "17", "-b:v", "0",
				"-pix_fmt", "yuv420p", file.toString())
				.redirectErrorStream(true)
				.redirectOutput(OUT.resolve(name + ".ffmpeg.log").toFile())
				.start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		free = new ArrayBlockingQueue<>(6);
		full = new ArrayBlockingQueue<>(64);
		for (int i = 0; i < 6; i++) {
			free.add(MemoryUtil.memAlloc(width * height * 4));
		}
		OutputStream out = ffmpeg.getOutputStream();
		writer = new Thread(() -> writeLoop(out), "film-writer");
		writer.start();
		clip = name;
		clipStartNanos = System.nanoTime();
		framesWritten = 0;
		LivelyVillagers.LOGGER.info("FILM client recording {} at {}x{}", name, width, height);
	}

	private static final ByteBuffer END = ByteBuffer.allocate(0);

	private static void writeLoop(OutputStream out) {
		WritableByteChannel ch = Channels.newChannel(out);
		ByteBuffer previous = null;
		try {
			while (true) {
				ByteBuffer b = full.take();
				if (b == END) {
					break;
				}
				ByteBuffer view = b.duplicate();
				view.position(0).limit(width * height * 4);
				while (view.hasRemaining()) {
					ch.write(view);
				}
				// Recycle a buffer once its last queued copy has been written.
				if (b != previous && previous != null && !full.contains(previous)) {
					free.offer(previous);
				}
				previous = b;
			}
			if (previous != null) {
				free.offer(previous);
			}
			out.close();
		} catch (IOException | InterruptedException e) {
			LivelyVillagers.LOGGER.error("FILM writer failed", e);
		}
	}

	private static void stopClip() {
		if (clip == null) {
			return;
		}
		double wallFramesAtStop = (System.nanoTime() - clipStartNanos) * FPS / 1e9;
		try {
			full.put(END);
			writer.join();
			ffmpeg.waitFor();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		double offset = (clipStartNanos - audioStartNanos) / 1e9;
		double wallFrames = wallFramesAtStop;
		if (wallFrames - framesWritten > 3) {
			LivelyVillagers.LOGGER.warn("FILM {} fell behind real time by {} frames", clip, (int) (wallFrames - framesWritten));
		}
		String line = String.format(java.util.Locale.ROOT, "{\"clip\":\"%s\",\"frames\":%d,\"fps\":%d,\"audio_offset\":%.3f}%n",
			clip, framesWritten, FPS, offset);
		try {
			Files.writeString(OUT.resolve("clips.jsonl"), line, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		LivelyVillagers.LOGGER.info("FILM client saved {} ({} frames, ffmpeg exit {})", clip, framesWritten, ffmpeg.exitValue());
		free.forEach(MemoryUtil::memFree);
		clip = null;
	}

	// ------------------------------------------------------------------ audio

	/** Records only this game's own output stream (not the rest of the desktop). */
	private static void startAudio() {
		try {
			String pid = String.valueOf(ProcessHandle.current().pid());
			Process list = new ProcessBuilder("pactl", "list", "sink-inputs").redirectErrorStream(true).start();
			String text = new String(list.getInputStream().readAllBytes());
			Files.writeString(OUT.resolve("sink-inputs.txt"), text);
			String index = null;
			for (String block : text.split("Sink Input #")) {
				boolean ours = block.contains("application.process.id = \"" + pid + "\"")
					|| block.contains("application.process.binary = \"java\"") || block.contains("node.name = \"java\"")
					|| block.contains("OpenAL Soft");
				if (ours) {
					Matcher m = Pattern.compile("^(\\d+)").matcher(block);
					if (m.find()) {
						index = m.group(1);
					}
				}
			}
			if (index == null) {
				LivelyVillagers.LOGGER.warn("FILM no audio stream for pid {}; recording video only", pid);
				return;
			}
			audio = new ProcessBuilder("parec", "--monitor-stream=" + index, "--format=s16le", "--rate=48000", "--channels=2",
				"--file-format=wav", OUT.resolve("game-audio.wav").toAbsolutePath().toString())
				.redirectErrorStream(true).redirectOutput(OUT.resolve("parec.log").toFile()).start();
			audioStartNanos = System.nanoTime();
			LivelyVillagers.LOGGER.info("FILM recording game audio from sink input {}", index);
		} catch (IOException e) {
			LivelyVillagers.LOGGER.warn("FILM audio capture unavailable", e);
		}
	}

	private static void stopAudio() {
		if (audio != null) {
			audio.destroy();
			audio = null;
		}
	}

	private FilmClient() {
	}
}
