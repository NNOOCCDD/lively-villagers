package com.livelyvillagers;

/** Dev-only: shared between the film director (server thread) and the recorder/camera (render thread). */
public final class FilmState {
	public record Pose(double x, double y, double z, float yaw, float pitch) {
	}

	@FunctionalInterface
	public interface CameraPath {
		Pose at(double seconds);
	}

	public static volatile boolean active;
	/** The clip being recorded, or null between clips. */
	public static volatile String recording;
	/** The camera path, and the System.nanoTime() its t=0 refers to. */
	public static volatile CameraPath camera;
	public static volatile long cameraStartNanos;
	public static volatile boolean finished;

	private FilmState() {
	}
}
