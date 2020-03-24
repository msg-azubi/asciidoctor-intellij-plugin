package org.asciidoc.intellij.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.asciidoc.intellij.namesValidator.AsciiDocRenameInputValidator;
import org.asciidoc.intellij.psi.AsciiDocBlockId;
import org.asciidoc.intellij.psi.AsciiDocRef;
import org.asciidoc.intellij.psi.AsciiDocReference;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;

/**
 * @author Alexander Schwartz 2020
 */
public class AsciiDocReferencePatternInspection extends AsciiDocInspectionBase {
  private static final String TEXT_HINT_ANCHOR_PATTERN_INVALID = "Anchor pattern invalid";
  private static final String TEXT_HINT_BLOCK_ID_PATTERN_INVALID = "Block ID pattern invalid";

  @NotNull
  @Override
  protected AsciiDocVisitor buildAsciiDocVisitor(@NotNull ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
    return new AsciiDocVisitor() {
      @Override
      public void visitElement(PsiElement o) {
        if (o instanceof AsciiDocRef) {
          for (PsiReference reference : o.getReferences()) {
            if (reference instanceof AsciiDocReference) {
              AsciiDocReference asciiDocReference = (AsciiDocReference) reference;
              if (!asciiDocReference.patternIsValid()) {
                String key = asciiDocReference.getRangeInElement().substring(o.getText());
                String explanation = explainDeviation(key);
                holder.registerProblem(o, TEXT_HINT_ANCHOR_PATTERN_INVALID + explanation, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, asciiDocReference.getRangeInElement());
              }
            }
          }
          super.visitElement(o);
        } else if (o instanceof AsciiDocBlockId) {
          AsciiDocBlockId blockId = (AsciiDocBlockId) o;
          if (!blockId.patternIsValid()) {
            String key = blockId.getName();
            String explanation = explainDeviation(key);
            holder.registerProblem(o, TEXT_HINT_BLOCK_ID_PATTERN_INVALID + explanation, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, TextRange.from(0, blockId.getTextLength()));
          }
          super.visitElement(o);
        }
      }
    };
  }

  @NotNull
  private String explainDeviation(String key) {
    String explanation = "";
    if (key.matches(".*\\s.*")) {
      explanation = ", must not contain spaces or tabs";
    } else {
      Matcher matcher = AsciiDocRenameInputValidator.BLOCK_ID_PATTERN.matcher(key);
      if (matcher.find()) {
        if (matcher.start() == 0) {
          explanation = ", problem starting at '" + key.substring(matcher.end(), matcher.end() + 1) + "'";
        } else {
          explanation = ", problem with prefix: '" + key.substring(0, matcher.start()) + "'";
        }
      }
    }
    return explanation;
  }
}
