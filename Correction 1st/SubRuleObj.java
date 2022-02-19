package org.languagetool.dev.archive;

// Smell Dedector, New Class
public class SubRuleObj {

  int subRuleCount;
  boolean inRuleGroup;

  public SubRuleObj(){
    subRuleCount = 0;
    inRuleGroup = false;
  }

  public SubRuleObj xmlLineContains(String xmlLine){

    if (xmlLine.contains("<rulegroup")) {
      this.subRuleCount = 0;
      this.inRuleGroup = true;
    } else if (xmlLine.contains("</rulegroup>")) {
      this.subRuleCount = 0;
      this.inRuleGroup = false;
    } else if ((xmlLine.contains("<rule ")||xmlLine.contains("<rule>")) && this.inRuleGroup) {
      this.subRuleCount++;
    }

    return this;
  }
}
