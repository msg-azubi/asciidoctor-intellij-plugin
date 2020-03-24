package org.asciidoc.intellij.psi;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import icons.AsciiDocIcons;
import org.asciidoc.intellij.namesValidator.AsciiDocRenameInputValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AsciiDocReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {
  private String key;

  public AsciiDocReference(@NotNull PsiElement element, TextRange textRange) {
    super(element, textRange);
    key = element.getText().substring(textRange.getStartOffset(), textRange.getEndOffset());
  }

  public boolean patternIsValid() {
    return AsciiDocRenameInputValidator.BLOCK_ID_PATTERN.matcher(key).matches();
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    Project project = myElement.getProject();
    final List<AsciiDocBlockId> ids = AsciiDocUtil.findIds(project, key);
    final List<AsciiDocSection> sections = AsciiDocFileUtil.findSections(project, key);
    List<ResolveResult> results = new ArrayList<>();
    for (AsciiDocBlockId id : ids) {
      results.add(new PsiElementResolveResult(id));
    }
    for (AsciiDocSection section : sections) {
      results.add(new PsiElementResolveResult(section));
    }
    if (results.size() > 0) {
      List<ResolveResult> sameFile = new ArrayList<>();
      PsiFile myFile = myElement.getContainingFile();
      for (ResolveResult result : results) {
        if (result.getElement() != null && result.getElement().getContainingFile() == myFile) {
          sameFile.add(result);
        }
      }
      if (sameFile.size() > 0) {
        results = sameFile;
      }
    }
    return results.toArray(new ResolveResult[0]);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    Project project = myElement.getProject();
    List<LookupElement> variants = new ArrayList<>();
    List<AsciiDocBlockId> ids = AsciiDocUtil.findIds(project);
    for (final AsciiDocBlockId id : ids) {
      if (id.getId() != null && id.getId().length() > 0) {
        variants.add(LookupElementBuilder.create(id)
          .withIcon(AsciiDocIcons.ASCIIDOC_ICON)
          .withTypeText(id.getContainingFile().getName())
        );
      }
    }
    // don't include list of autogenerated IDs for sections here as they will have suffixes if they appear multiple times
    return variants.toArray();
  }
}
