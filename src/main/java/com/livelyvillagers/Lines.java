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
		PANIC_HOSTILE, PANIC_HURT, PANIC_TNT, PANIC_LOOP, CALM,
		CLICK_JOBLESS, CLICK_WORKER, CLICK_BABY,
		BEDTIME, CLICK_JOBLESS_NIGHT, CLICK_WORKER_NIGHT, SLEEP_TALK,
		RAID_START, RAID_PANIC, RAID_HERO, RAID_WON, RAID_LOST,
		DEATH, DEATH_BY_PLAYER, DEATH_BY_RAIDER
	}

	private static final Map<Topic, String[]> DEFAULT = new EnumMap<>(Topic.class);
	private static final Map<Personality, Map<Topic, String[]>> BY_PERSONALITY = new EnumMap<>(Personality.class);

	static {
		put(Topic.GREET, "Hello, {player}!", "Hrm. Hi there.", "Good day!", "Oh, hello!", "Hmm-hm!", "Nice to see you.");
		put(Topic.GREET_WARM, "{player}! My favourite customer!", "Ah, {player}! Always a pleasure.", "Hello again, friend!");
		put(Topic.GREET_COLD, "Hmph.", "...", "I'm watching you, {player}.", "Oh. It's you.");
		put(Topic.GREET_NIGHT, "I'm sleepy...", "I'm so tired.", "Time to turn in for the night.", "*yawn* Evening, {player}.",
			"Long day. Goodnight!", "Out this late? Careful.", "Shouldn't you be indoors?", "Can barely keep my eyes open...");
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

		put(Topic.CLICK_JOBLESS, "Hey...", "Stop touching me!", "Do you mind?", "Personal space, please.",
			"Hrmm! Hands off!", "I don't have anything for you.", "No job, no trades. Sorry.", "Poke me one more time...");
		put(Topic.CLICK_WORKER, "Hello! Welcome to my shop!", "Ah, a customer! Take a look.", "Best prices in the village!",
			"What can I get for you today?", "Emeralds only, friend.", "Fresh stock today!", "The {job} is in! How can I help?",
			"Pleasure doing business, {player}.");
		put(Topic.CLICK_BABY, "Hehe! That tickles!", "Tag, you're it!", "Hey! I'm playing!");

		put(Topic.BEDTIME, "Time to turn in for the night.", "*yaaawn*", "I'm so tired...", "Bedtime already?",
			"Goodnight, everyone!", "My feet are killing me. Bed!", "I'm sleepy. See you tomorrow.");
		put(Topic.CLICK_JOBLESS_NIGHT, "Hey... it's bedtime.", "Stop poking me, I'm sleepy!", "Let me sleep...", "Too tired for this.");
		put(Topic.CLICK_WORKER_NIGHT, "*yawn* Shop's nearly closed...", "Make it quick, I'm sleepy.", "Late customer, eh? Fine, fine.",
			"I'm so tired... what do you need?");
		put(Topic.SLEEP_TALK, "Zzz...", "*snore*", "Five more minutes...", "Mmh... emeralds...", "Zzz... no... my crops...");

		put(Topic.RAID_START, "Raiders! Everyone inside!", "Pillagers are coming!", "Ring the bell! RING THE BELL!",
			"Lock the doors!", "Not again!", "Hide the emeralds!");
		put(Topic.RAID_PANIC, "They're at the door!", "Hide! HIDE!", "Where's the iron golem when you need it?!",
			"Save the emeralds!", "{threat}! Get away from me!", "Help! Somebody!", "Not my crops!");
		put(Topic.RAID_HERO, "{player}! Please, save us!", "A hero! Thank goodness you're here!", "{player}, they went that way!",
			"Don't let them take the village, {player}!");
		put(Topic.RAID_WON, "We did it! We're saved!", "Hooray for {player}!", "Three cheers for the hero!",
			"Phew... is everyone okay?", "They're gone! They're really gone!");
		put(Topic.RAID_LOST, "Our poor village...", "They took everything...", "Is anyone left?", "We'll rebuild... somehow.");
		put(Topic.DEATH, "Tell my family... I loved them...", "Not like this...", "Hrrmm... goodbye...",
			"My emeralds... take care of them...", "Avenge me!", "I never finished my trades...");
		put(Topic.DEATH_BY_PLAYER, "Why... {player}?", "I thought we were friends...", "I'll remember this... oh wait.");
		put(Topic.DEATH_BY_RAIDER, "Curse you... pillagers...", "Protect... the village...", "Avenge me, {player}!");

		put(Personality.CHEERFUL, Topic.DEATH, "It was a good life!", "Tell everyone... I said hi!");
		put(Personality.GRUMPY, Topic.DEATH, "Typical.", "Hmph. Figures.", "Of course this happens to ME.");
		put(Personality.SHY, Topic.DEATH, "...bye.", "Oh... oh no...");
		put(Personality.CURIOUS, Topic.DEATH, "So THIS is what's next...", "Ooh, a bright light!");
		put(Personality.GRUMPY, Topic.RAID_START, "Raiders. Of course. Today of all days.", "Hrmph! Everyone inside!");
		put(Personality.SHY, Topic.RAID_PANIC, "*hides under the bed*", "Eep! Eep! Eep!");
		put(Personality.CHEERFUL, Topic.RAID_WON, "Party at the bell! Everyone's invited!", "You're amazing, {player}!");

		put(Personality.CHEERFUL, Topic.GREET_NIGHT, "Goodnight, {player}! Sweet dreams!", "*yawn* What a lovely day it was!");
		put(Personality.CHEERFUL, Topic.BEDTIME, "Nighty night, everyone!", "Can't wait for tomorrow! *yawn*");
		put(Personality.GRUMPY, Topic.GREET_NIGHT, "It's late. Go home.", "Hrmph. Some of us sleep.");
		put(Personality.GRUMPY, Topic.BEDTIME, "Finally. Bed.", "Hrmph. Nobody better wake me.");
		put(Personality.GRUMPY, Topic.CLICK_WORKER_NIGHT, "We're CLOSED. ...Fine. What?", "Do you know what time it is?");
		put(Personality.SHY, Topic.GREET_NIGHT, "...g-goodnight.", "*sleepy wave*");
		put(Personality.CURIOUS, Topic.GREET_NIGHT, "Ooh, do you see the stars tonight?", "*yawn* Did you know zombies come out at night?");

		put(Personality.CHEERFUL, Topic.CLICK_JOBLESS, "Hey... I'm on a break. Forever.", "Hi! ...No, I still don't have a job.");
		put(Personality.CHEERFUL, Topic.CLICK_WORKER, "Hello hello! Take your time!", "Ooh, a customer! My favourite!", "Hi {player}! Great deals today!");
		put(Personality.GRUMPY, Topic.CLICK_JOBLESS, "Stop touching me!", "Go bother someone with a job.", "HRMM!");
		put(Personality.GRUMPY, Topic.CLICK_WORKER, "Buying or browsing?", "No haggling.", "Make it quick.");
		put(Personality.SHY, Topic.CLICK_JOBLESS, "Eep!", "P-please don't...", "...hey.");
		put(Personality.SHY, Topic.CLICK_WORKER, "Oh! Um... w-welcome.", "...would you like to trade?");
		put(Personality.CURIOUS, Topic.CLICK_JOBLESS, "Hey... what's that you're holding?", "Do you know where I could find a job?");
		put(Personality.CURIOUS, Topic.CLICK_WORKER, "Welcome! Got anything interesting to sell?", "Ooh, where are you from? ...Oh, trading, right!");

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
