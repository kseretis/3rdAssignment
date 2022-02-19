package org.languagetool.dev;

import org.jetbrains.annotations.NotNull;
import org.languagetool.JLanguageTool;
import org.languagetool.Languages;
import org.languagetool.rules.Rule;
import org.languagetool.rules.spelling.SpellingCheckRule;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Print words from CSV that are unknown to the spell checker, sorted by number of occurrences.
 */
public class UnknownWordFinderForCsv extends SpellingCheckRuleKS{

  private void run(File dir, JLanguageTool lt) throws IOException {
    SpellingCheckRule spellerRule = getSpellingCheckRule(lt);
    List<Path> files = Files.walk(dir.toPath()).filter(Files::isRegularFile).collect(Collectors.toList());
    for (Path file : files) {
      handle(file, spellerRule);
    }
  }

//  *---------------------------------- old code ----------------------------------*
//  @NotNull
//  private SpellingCheckRule getSpellingCheckRule(JLanguageTool lt) {
//    SpellingCheckRule spellerRule = null;
//    for (Rule rule : lt.getAllActiveRules()) {
//      if (rule.isDictionaryBasedSpellingRule()) {
//        if (spellerRule != null) {
//          throw new RuntimeException("Found more than one spell rule: " + rule + ", " + spellerRule);
//        }
//        spellerRule = (SpellingCheckRule) rule;
//      }
//    }
//    if (spellerRule == null) {
//      throw new RuntimeException("No speller rule found for " + lt.getLanguage());
//    }
//    return spellerRule;
//  }
//  *------------------------------------------------------------------------------*

  private void handle(Path f, SpellingCheckRule rule) throws IOException {
    int i = 0;
    if (f.toString().toLowerCase().endsWith(".csv")) {
      List<String> lines = Files.readAllLines(f);
      for (String line : lines) {
        String[] parts = line.split(",");
        String word = parts[0].replace("\"", "");
        if (rule.isMisspelled(word)) {
          System.out.println(line);
          i++;
        }
      }
    } else {
      System.out.println("Ignoring " + f + ": unknown suffix");
    }
    System.out.println("Lines printed: " + i);
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.out.println("Usage: " + UnknownWordFinderForCsv.class.getSimpleName() +  " <langCode> <dir>");
      System.exit(1);
    }
    JLanguageTool lt = new JLanguageTool(Languages.getLanguageForShortCode(args[0]));
    new UnknownWordFinderForCsv().run(new File(args[1]), lt);
  }
}
