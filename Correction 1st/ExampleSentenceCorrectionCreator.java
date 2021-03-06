package org.languagetool.dev.archive;

import org.apache.commons.io.IOUtils;
import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.Languages;
import org.languagetool.rules.IncorrectExample;
import org.languagetool.rules.Rule;
import org.languagetool.rules.RuleMatch;
import org.languagetool.rules.patterns.AbstractPatternRule;
import org.languagetool.rules.patterns.PatternRule;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ugly hack for one-time-use only: add 'correction' attributes to
 * example sentences automatically.
 */
final class ExampleSentenceCorrectionCreator {

  private int addedCorrectionsCount = 0;

  private void run(Language lang) throws IOException {
    File basePath = new File("/lt/git/languagetool/languagetool-language-modules");
    if (!basePath.exists()) {
      throw new RuntimeException("basePath does not exist: " + basePath);
    }
    String langCode = lang.getShortCode();
    File xml = new File(basePath, "/" + langCode + "/src/main/resources/org/languagetool/rules/" + langCode + "/grammar.xml");
    List<String> xmlLines = IOUtils.readLines(new FileReader(xml));
    JLanguageTool tool = new JLanguageTool(lang);
    for (Rule rule : tool.getAllRules()) {
      if (!(rule instanceof PatternRule)) {
        continue;
      }
      List<IncorrectExample> incorrectExamples = rule.getIncorrectExamples();
      for (IncorrectExample incorrectExample : incorrectExamples) {
        checkCorrections(rule, incorrectExample, xmlLines, tool);
      }
    }
    System.err.println("Added corrections: " + addedCorrectionsCount);
    for (String xmlLine : xmlLines) {
      System.out.println(xmlLine);
    }
  }

  private void checkCorrections(Rule rule, IncorrectExample incorrectExample, List<String> xmlLines, JLanguageTool tool) throws IOException {
    List<String> corrections = incorrectExample.getCorrections();
    if (corrections.isEmpty()) {
      for (Rule r : tool.getAllActiveRules()) {
        tool.disableRule(r.getId());
      }
      tool.enableRule(rule.getId());
      String incorrectSentence = incorrectExample.getExample().replaceAll("</?marker>", "");
      List<RuleMatch> matches = tool.check(incorrectSentence);
      System.err.println("no corrections: " + rule.getId() + ", " + matches.size() + " matches");
      if (matches.isEmpty()) {
        throw new RuntimeException("Got no rule match: " + incorrectSentence);
      }
      List<String> suggestedReplacements = matches.get(0).getSuggestedReplacements();
      String newAttribute = "correction=\"" + String.join("|", suggestedReplacements) + "\"";
      addAttribute(rule, newAttribute, xmlLines);
    }
  }

  // Note: this is a bad hack, we just iterate through the file's lines
  private void addAttribute(Rule rule, String newAttribute, List<String> xmlLines) {
    List<Integer> linesToModify = new ArrayList<>();
    String currentRuleId = null;
    Pattern pattern = Pattern.compile(".*id=[\"'](.*?)[\"'].*");
    String expectedSubId = ((AbstractPatternRule) rule).getSubId();
    int lineCount = 0;
//    int subRuleCount = 0; //Smell Detector, Old code
    int modifyCount = 0;
//    boolean inRuleGroup = false;  //Smell Detector, Old code
    SubRuleObj subRuleObj = new SubRuleObj(); //Smell Detector, Added
    for (String xmlLine : xmlLines) {
//  *---------------------------------- old code ----------------------------------*
//      if (xmlLine.contains("<rulegroup")) {
//        subRuleCount = 0;
//        inRuleGroup = true;
//      } else if (xmlLine.contains("</rulegroup>")) {
//        subRuleCount = 0;
//        inRuleGroup = false;
//      } else if ((xmlLine.contains("<rule ")||xmlLine.contains("<rule>")) && inRuleGroup) {
//        subRuleCount++;
//      }
//  *------------------------------------------------------------------------------*
      subRuleObj.xmlLineContains(xmlLine);  //Smell Detector, Added
      Matcher m = pattern.matcher(xmlLine);
      if (m.matches()) {
        currentRuleId = m.group(1);
      }
      if (xmlLine.contains("type=\"incorrect\"") || xmlLine.contains("type='incorrect'")) {
        if (currentRuleId != null && !currentRuleId.equals(rule.getId())) {
          lineCount++;
          continue;
        }
        if (!subRuleObj.inRuleGroup) {    //Smell Detector, Changed
          subRuleObj.subRuleCount = 1;    //Smell Detector, Changed
        }
        if (!expectedSubId.equals("0") && !expectedSubId.equals(String.valueOf(subRuleObj.subRuleCount))) { //Smell Detector, Changed
          lineCount++;
          continue;
        }
        linesToModify.add(lineCount);
        break;
      }
      lineCount++;
    }
    for (Integer s : linesToModify) {
      String newLine = xmlLines.get(s).replaceFirst("type=[\"']incorrect[\"']", newAttribute);
      xmlLines.set(s, newLine);
      addedCorrectionsCount++;
      modifyCount++;
    }
    if (modifyCount == 0) {
      System.err.println("No line modified: " + rule + "[" + expectedSubId + "]");
    }
  }

  public static void main(String[] args) throws IOException {
    ExampleSentenceCorrectionCreator prg = new ExampleSentenceCorrectionCreator();
    prg.run(Languages.getLanguageForShortCode("de"));
  }

}
