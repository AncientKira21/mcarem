package com.ancientkira.mca.entity.ai;

import java.util.*;
import java.util.regex.Pattern;

/** Keep most of a localized sentence understandable; vary the garbled words per utterance. */
public final class ZombieSpeech {
    private static final Pattern WORD = Pattern.compile("[\\p{L}]+(?:['’][\\p{L}]+)*");
    private static final String[] SOUNDS = {"grr", "urrgh", "hrngh", "aargh", "mmrr", "rragh"};
    public static String garble(String sentence, long seed) {
        var matches = WORD.matcher(sentence).results().toList();
        if (matches.isEmpty()) return sentence;
        var random = new Random(seed);
        if (matches.size() == 1 && random.nextInt(4) != 0) return sentence;
        var indices = new ArrayList<Integer>();
        for (int i=0;i<matches.size();i++) indices.add(i);
        Collections.shuffle(indices,random);
        var selected = new HashSet<>(indices.subList(0,1+random.nextInt(Math.max(1,matches.size()/3))));
        var result = new StringBuilder();
        int cursor=0;
        for(int i=0;i<matches.size();i++) {
            var match=matches.get(i);
            result.append(sentence,cursor,match.start());
            result.append(selected.contains(i)?SOUNDS[random.nextInt(SOUNDS.length)]:match.group());
            cursor=match.end();
        }
        return result.append(sentence,cursor,sentence.length()).toString();
    }
}
