package org.languagetool.dev;

import org.jetbrains.annotations.NotNull;
import org.languagetool.JLanguageTool;
import org.languagetool.rules.Rule;
import org.languagetool.rules.spelling.SpellingCheckRule;

public class SpellingCheckRuleKS {

  @NotNull
  public SpellingCheckRule getSpellingCheckRule(JLanguageTool lt) {
    SpellingCheckRule spellerRule = null;
    for (Rule rule : lt.getAllActiveRules()) {
      if (rule.isDictionaryBasedSpellingRule()) {
        if (spellerRule != null) {
          throw new RuntimeException("Found more than one spell rule: " + rule + ", " + spellerRule);
        }
        spellerRule = (SpellingCheckRule) rule;
      }
    }
    if (spellerRule == null) {
      throw new RuntimeException("No speller rule found for " + lt.getLanguage());
    }
    return spellerRule;
  }

}
