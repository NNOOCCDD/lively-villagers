package com.livelyvillagers;

import net.minecraft.util.RandomSource;

import java.util.EnumMap;
import java.util.Map;

/** Everything villagers say. Placeholders: {name} {player} {block} {threat} {job} {item}. */
public final class Lines {
	public enum Topic {
		GREET, GREET_WARM, GREET_COLD, GREET_NIGHT, GREET_RAIN, GREET_BABY, INTRO,
		LOVE_JOB_SITE, WANT_JOB_SITE, LIKE_BLOCK, DISLIKE_BLOCK, SCARY_BLOCK, TOO_CLOSE, CURIOUS_BLOCK,
		BROKE_JOB_SITE, BROKE_BED, BROKE_BELL, BROKE_LIKED,
		GIFT_LIKE, GIFT_LOVE, GIFT_AGAIN, FOUND_GIFT,
		PANIC_HOSTILE, PANIC_HURT, PANIC_TNT, PANIC_LOOP, CALM
	}

	private static final Map<Topic, String[]> DEFAULT = new EnumMap<>(Topic.class);
	private static final Map<Personality, Map<Topic, String[]>> BY_PERSONALITY = new EnumMap<>(Personality.class);

	static {
		put(Topic.GREET, "Hello, {player}!", "Hrm. Hi there.", "Good day!", "Oh, hello!", "Hmm-hm!", "Nice to see you.");
		put(Topic.GREET_WARM, "{player}! My favourite customer!", "Ah, {player}! Always a pleasure.", "Hello again, friend!");
		put(Topic.GREET_COLD, "Hmph.", "...", "I'm watching you, {player}.", "Oh. It's you.");
		put(Topic.GREET_NIGHT, "Out this late? Careful.", "*yawn* Evening.", "Shouldn't you be indoors?");
		put(Topic.GREET_RAIN, "Lovely weather for crops!", "Wet day, isn't it?", "Hrm, rain again.");
		put(Topic.GREET_BABY, "Hi!! Hi hi!", "Wanna play tag?", "You're tall!");
		put(Topic.INTRO, "I'm {name} the {job}. People say I'm {personality}.", "Name's {name}. {job}. Quite {personality}, they tell me.");

		put(Topic.LOVE_JOB_SITE, "A new {block}! Just what I needed!", "Ooh, a {block}! Can I use it?", "Now THAT is a fine {block}.");
		put(Topic.WANT_JOB_SITE, "A {block}... I could work with that!", "Is that {block} for me? I need a job!");
		put(Topic.LIKE_BLOCK, "Ooh, pretty!", "Nice {block}!", "That brightens the place up.", "Hm-hmm! Lovely.");
		put(Topic.DISLIKE_BLOCK, "Ugh, a {block}? Here?", "Not in my village, please.", "Hrrm. I don't like that.");
		put(Topic.SCARY_BLOCK, "Is that... TNT?!", "Put that away!", "That's dangerous!");
		put(Topic.TOO_CLOSE, "Hey, watch it!", "Whoa! Nearly hit me!", "Mind my toes!");
		put(Topic.CURIOUS_BLOCK, "Hm, {block}. Interesting.", "What are you building?", "Ooh, what's that for?");

		put(Topic.BROKE_JOB_SITE, "My {block}! How will I work now?!", "Hey! I was using that!");
		put(Topic.BROKE_BED, "That was my bed!", "Where am I supposed to sleep?!");
		put(Topic.BROKE_BELL, "Not the bell! How will we warn everyone?");
		put(Topic.BROKE_LIKED, "Aww, I liked that.", "Why'd you break it?");

		put(Topic.GIFT_LIKE, "For me? Thank you, {player}!", "A {item}! How kind!", "Aww, you shouldn't have!");
		put(Topic.GIFT_LOVE, "WOW! A {item}! You're the best, {player}!", "I'll treasure this forever!", "Oh my! Thank you thank you!");
		put(Topic.GIFT_AGAIN, "Another one? You're too kind.", "Hehe, thanks again!");
		put(Topic.FOUND_GIFT, "Ooh, a {item}! Finders keepers.", "Someone dropped a {item}!");

		put(Topic.PANIC_HOSTILE, "{threat}! RUN!", "Help! A {threat}!", "Aaah! {threat}!");
		put(Topic.PANIC_HURT, "Ow! What was that for?!", "Help! Stop it!", "Ouch!");
		put(Topic.PANIC_TNT, "TNT! Get away!", "It's gonna blow!", "Run for it!");
		put(Topic.PANIC_LOOP, "Aaaah!", "Help!", "Hrmmm!!", "Somebody!");
		put(Topic.CALM, "Phew... that was close.", "Is it gone?", "My heart is still racing.");

		put(Personality.CHEERFUL, Topic.GREET, "Hello hello, {player}!", "What a wonderful day!", "Hi {player}! Love the outfit!", "Oh, hi! Come again soon!");
		put(Personality.CHEERFUL, Topic.LIKE_BLOCK, "Oh, how lovely!", "I adore a nice {block}!", "Yay, decorations!");
		put(Personality.SHY, Topic.GREET, "...h-hi.", "Oh! Um. Hello.", "*waves quietly*");
		put(Personality.SHY, Topic.GIFT_LIKE, "F-for me? ...thank you.", "*blushes*");
		put(Personality.SHY, Topic.INTRO, "I-I'm {name}. The {job}.", "...{name}. Nice to meet you.");
		put(Personality.GRUMPY, Topic.GREET, "What do you want?", "Hrmph.", "Don't touch my stuff.", "Mm.");
		put(Personality.GRUMPY, Topic.GIFT_LIKE, "...Fine. Thanks, I suppose.", "Hmph. A {item}. ...It's nice.");
		put(Personality.GRUMPY, Topic.LIKE_BLOCK, "Hmph. Could be worse.", "Not bad. Not great.");
		put(Personality.GRUMPY, Topic.CALM, "Hmph. Typical.", "Nobody ever warns me.");
		put(Personality.CURIOUS, Topic.GREET, "Hello! Where did you travel from?", "Oh, hi! What's in your pack?", "Ooh, an adventurer!");
		put(Personality.CURIOUS, Topic.LIKE_BLOCK, "Ooh, how does a {block} work?", "Fascinating {block}!");
	}

	private static void put(Topic topic, String... lines) {
		DEFAULT.put(topic, lines);
	}

	private static void put(Personality p, Topic topic, String... lines) {
		BY_PERSONALITY.computeIfAbsent(p, k -> new EnumMap<>(Topic.class)).put(topic, lines);
	}

	public static String pick(Topic topic, Personality personality, RandomSource random) {
		String[] lines = BY_PERSONALITY.getOrDefault(personality, Map.of()).get(topic);
		// Personality lines replace the defaults most of the time, not always.
		if (lines == null || random.nextInt(4) == 0) {
			lines = DEFAULT.get(topic);
		}
		return lines[random.nextInt(lines.length)];
	}

	private Lines() {
	}
}
