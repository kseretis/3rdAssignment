package org.languagetool.dev.archive;

import com.google.common.base.Strings;
import com.google.common.xml.XmlEscapers;
import org.apache.commons.io.IOUtils;
import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.Languages;
import org.languagetool.rules.Rule;
import org.languagetool.rules.patterns.PatternRule;
import org.languagetool.rules.patterns.PatternToken;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces the 'pattern' element in simple rules with the 'regexp' element.
 * WARNING: this is a hack, the rules it produces need to be checked and modified manually!
 */
final class RuleSimplifier {

  private int touchedRulesCount;

  private void run(Language lang) throws IOException {
    File basePath = new File("/lt/git/languagetool/languagetool-language-modules");
    if (!basePath.exists()) {
      throw new RuntimeException("basePath does not exist: " + basePath);
    }
    String langCode = lang.getShortCode();
    File xml = new File(basePath, "/" + langCode + "/src/main/resources/org/languagetool/rules/" + langCode + "/grammar.xml");
    List<String> xmlLines = IOUtils.readLines(new FileReader(xml));
    JLanguageTool tool = new JLanguageTool(lang);
    int totalRules = 0;
    for (Rule rule : tool.getAllActiveRules()) {
      if (!(rule instanceof PatternRule)) {
        continue;
      }
      PatternRule patternRule = (PatternRule) rule;
      String id = patternRule.getFullId();
      if (isSimple((PatternRule)rule)) {
        System.err.println("Simplifying: " + id);
        simplify(patternRule, xmlLines);
      } else {
        System.err.println("Can't simplify: " + id);
      }
      totalRules++;
    }
    System.err.println("touchedRulesCount: " + touchedRulesCount + " out of " + totalRules);
    for (String xmlLine : xmlLines) {
      System.out.println(xmlLine);
    }
  }

  private boolean isSimple(PatternRule rule) {
    return rule.getPatternTokens().stream().allMatch(this::isSimple)
            && rule.getStartPositionCorrection() == 0
            && rule.getEndPositionCorrection() == 0;
  }
  
  private boolean isSimple(PatternToken t) {
    return !(t.getNegation() || t.getPOSNegation() || t.hasAndGroup() || t.hasExceptionList() ||
            t.hasNextException() || t.hasOrGroup() || t.isInflected() || t.isPOStagRegularExpression() ||
            t.getPOStag() != null || t.isReferenceElement() || t.isSentenceStart() ||
            t.getSkipNext() != 0);
  }

  private String getRegex(PatternRule rule) {
    StringBuilder sb = new StringBuilder();
    List<PatternToken> tokens = rule.getPatternTokens();
    boolean hasCSParts = tokens.stream().anyMatch(PatternToken::isCaseSensitive);
    boolean allCSParts = tokens.stream().allMatch(PatternToken::isCaseSensitive);
    for (PatternToken patternToken : rule.getPatternTokens()) {
      String str = patternToken.getString();
      boolean setAllParenthesis = containsBackRef(rule.getMessage()) || containsBackRef(rule.getSuggestionsOutMsg());
      if (hasCSParts && !allCSParts && !patternToken.isCaseSensitive()) {
        sb.append("(?i:");
        appendTokenString(sb, str, setAllParenthesis);
        sb.append(')');
      } else {
        appendTokenString(sb, str, setAllParenthesis);
      }
      sb.append(' ');
    }
    String escapedRegex = XmlEscapers.xmlContentEscaper().escape(sb.toString().trim());
    if (allCSParts) {
      return "<regexp case_sensitive='yes'>" + escapedRegex + "</regexp>";
    }
    return "<regexp>" + escapedRegex + "</regexp>";
  }

  private boolean containsBackRef(String str) {
    return str.matches(".*\\\\\\d+.*");
  }
  
  private void appendTokenString(StringBuilder sb, String str, boolean setAllParenthesis) {
    if (str.contains("|") || setAllParenthesis) {
      sb.append('(').append(str).append(')');
    } else {
      sb.append(str);
    }
  }

  // Note: this is a bad hack, we just iterate through the file's lines
  private void simplify(PatternRule rule, List<String> xmlLines) {
    List<Integer> linesToRemove = new ArrayList<>();
    String currentRuleId = null;
    Pattern pattern = Pattern.compile(".*id=[\"'](.*?)[\"'].*");
    String expectedSubId = rule.getSubId();
//    int lineCount = 0;  //Smell Detector, Old code
//    int subRuleCount = 0; //Smell Detector, Old code
//    int removedCount = 0; //Smell Detector, Old code
//    boolean inRuleGroup = false;  //Smell Detector, Old code
    SubRuleObj subRuleObj = new SubRuleObj(); //Smell Detector, Added
    String newRegex = null;
    boolean inAntiPattern = false;
    for (int lineCount = 0; lineCount < xmlLines.size(); lineCount++) {
//    for (String xmlLine : xmlLines) {
      String xmlLine = xmlLines.get(lineCount);
      subRuleObj.xmlLineContains(xmlLine);//Smell Detector, Added
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
      Matcher m = pattern.matcher(xmlLine);
      if (m.matches()) {
        currentRuleId = m.group(1);
      }
      if (currentRuleId != null && !currentRuleId.equals(rule.getId())) {
        continue;
      }
      if (!subRuleObj.inRuleGroup) {    //Smell Detector
        subRuleObj.subRuleCount = 1;    //Smell Detector
      }
      if (!expectedSubId.equals("0") && !expectedSubId.equals(String.valueOf(subRuleObj.subRuleCount))) {
        continue;
      }
      if (xmlLine.matches(".*<antipattern.*")) {
        inAntiPattern = true;
      }
      if (inAntiPattern) {
        continue;
      }
      if (xmlLine.matches(".*</antipattern.*")) {
        inAntiPattern = false;
        continue;
      }
//  *---------------------------------- old code ----------------------------------*
//      if (xmlLine.matches(".*<(token|pattern).*") || xmlLine.matches("\\s*</?marker>.*")) {
//        linesToRemove.add(lineCount);
//      }
//      if (xmlLine.matches(".*</pattern.*")) {
//        linesToRemove.add(lineCount);
//        int lastTokenIndent = xmlLine.indexOf('<');
//        newRegex = Strings.repeat(" ", lastTokenIndent) + getRegex(rule);
//      }
//  *------------------------------------------------------------------------------*
      newRegex = splitSimplify1(xmlLine, linesToRemove, lineCount, rule); //Smell Detector, Added
    }
//  *---------------------------------- old code ----------------------------------*
//    Collections.reverse(linesToRemove); // start from end, as we need to remove items
//    for (Integer s : linesToRemove) {
//      xmlLines.remove(s.intValue());
//      removedCount++;
//    }
//    if (removedCount == 0) {
//      System.err.println("No line removed: " + rule + "[" + expectedSubId + "]");
//    } else {
//      xmlLines.add(linesToRemove.get(linesToRemove.size()-1), newRegex);
//      touchedRulesCount++;
//    }
//  *------------------------------------------------------------------------------*
      splitSimplify2(linesToRemove, xmlLines, rule, newRegex, expectedSubId); //Smell Detector, Added
  }

  //Smell Detector, Added
  public String splitSimplify1(String xmlLine, List<Integer> linesToRemove, int lineCount, PatternRule rule){

    if (xmlLine.matches(".*<(token|pattern).*") || xmlLine.matches("\\s*</?marker>.*")) {
      linesToRemove.add(lineCount);
    }
    if (xmlLine.matches(".*</pattern.*")) {
      linesToRemove.add(lineCount);
      int lastTokenIndent = xmlLine.indexOf('<');
      return Strings.repeat(" ", lastTokenIndent) + getRegex(rule);
    }
    return null;
  }

  //Smell Detector, Added
  public void splitSimplify2(List<Integer> linesToRemove, List<String> xmlLines, PatternRule rule,
                                  String newRegex, String expectedSubId){
    int removedCount = 0;

    Collections.reverse(linesToRemove); // start from end, as we need to remove items
    for (Integer s : linesToRemove) {
      xmlLines.remove(s.intValue());
      removedCount++;
    }
    if (removedCount == 0) {
      System.err.println("No line removed: " + rule + "[" + expectedSubId + "]");
    } else {
      xmlLines.add(linesToRemove.get(linesToRemove.size()-1), newRegex);
      touchedRulesCount++;
    }
  }

  public static void main(String[] args) throws IOException {
    RuleSimplifier prg = new RuleSimplifier();
    prg.run(Languages.getLanguageForShortCode("de"));
  }

}
