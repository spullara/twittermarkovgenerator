package twittermarkovchain;

import com.google.common.collect.ImmutableMap;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  @Argument(alias = "u", description = "Username of the person to immitate")
  private static String user = "dickc";

  static class Pair {
    final String s1;
    final String s2;

    Pair(String s1, String s2) {
      this.s1 = s1;
      this.s2 = s2;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Pair pair = (Pair) o;

      if (s1 != null ? !s1.equals(pair.s1) : pair.s1 != null) return false;
      if (s2 != null ? !s2.equals(pair.s2) : pair.s2 != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = s1 != null ? s1.hashCode() : 0;
      result = 31 * result + (s2 != null ? s2.hashCode() : 0);
      return result;
    }
  }

  public static void main(String[] args) throws TwitterException, IOException {
    Args.parseOrExit(Main.class, args);
    Twitter twitter = TwitterFactory.getSingleton();

    List<String> tweets = new ArrayList<>();
    File file = new File(user + ".txt");
    if (file.exists()) {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
      String line;
      while ((line = br.readLine()) != null) {
        tweets.add(line);
      }
    } else {
      BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
      int total = 0;
      int page = 1;
      int size;
      do {
        ResponseList<Status> statuses = twitter.timelines().getUserTimeline(user, new Paging(page++, 200));
        size = statuses.size();
        total += size;

        for (Status status : statuses) {
          if (status.getInReplyToUserId() == -1 && status.getRetweetedStatus() == null) {
            String text = status.getText().replaceAll("\n", " ");
            bw.write(text);
            bw.newLine();
            tweets.add(text);
          }
        }
      } while (size > 0);
      bw.close();
    }

    // We need to generate a map of pair frequencies indexed by the first in the pair
    Map<String, Map<String, Integer>> frequencyMap = tweets.stream()
            .flatMap((String s) -> {
              Stream.Builder<Pair> builder = Stream.builder();
              String last = null;
              for (String current : s.toLowerCase().replaceAll("https?://.+\\b", "").replaceAll("[^a-z@# ]", "").split(" ")) {
                if (current.equals("")) continue;
                if (last == null) {
                  builder.add(new Pair("", current));
                } else {
                  builder.add(new Pair(last, current));
                }
                last = current;
              }
              if (last != null) {
                builder.add(new Pair(last, ""));
              }
              return builder.build();
            })
            .collect(Collectors.toMap(p -> p.s1, p -> ImmutableMap.of(p.s2, 1), (m1, m2) -> {
              HashMap<String, Integer> newmap = new HashMap<>(m1);
              for (Map.Entry<String, Integer> e : m2.entrySet()) {
                String key = e.getKey();
                Integer integer = newmap.get(key);
                if (integer == null) {
                  newmap.put(key, 1);
                } else {
                  newmap.put(key, integer + e.getValue());
                }
              }
              return newmap;
            }));

    // Random!
    Random random = new SecureRandom();

    for (int i = 0; i < 10; i++) {
      // Now that we have the frequency map we can generate a message.
      String word = "";
      do {
        Map<String, Integer> distribution = frequencyMap.get(word);
        int total = 0;
        for (Map.Entry<String, Integer> e : distribution.entrySet()) {
          total += e.getValue();
        }
        int which = random.nextInt(total);
        int current = 0;
        for (Map.Entry<String, Integer> e : distribution.entrySet()) {
          Integer value = e.getValue();
          if (which >= current && which < current + value) {
            word = e.getKey();
          }
          current += value;
        }
        System.out.print(word);
        System.out.print(" ");
      } while (!word.equals(""));
      System.out.println();
    }
  }
}
