package org.asciidoc.intellij.grazie;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.TokenSet;
import org.asciidoc.intellij.lexer.AsciiDocTokenTypes;
import org.asciidoc.intellij.parser.AsciiDocElementTypes;
import org.asciidoc.intellij.psi.AsciiDocAttributeDeclarationImpl;
import org.asciidoc.intellij.psi.AsciiDocInlineMacro;
import org.asciidoc.intellij.psi.AsciiDocLink;
import org.asciidoc.intellij.psi.AsciiDocUrl;
import org.jetbrains.annotations.NotNull;

public class AsciiDocLanguageSupport {

  public enum Behavior {
    /**
     * A PSI element that contains a nested text and should be ignored.
     * <p>
     * Example: The "Headline" is a nested element in the section; it is treated as its own sentence.
     * <p>
     * <pre>
     * == Headline
     * More text
     * </pre>
     * <p>
     */
    ABSORB,
    /**
     * A PSI element that <b>WILL NOT</b> be printed, adjacent text is part of the same word.
     * <p>
     * Example: the "**" would be STEALTH<br>
     * <code>**b**old</code> is one word "bold"
     */
    STEALTH,
    /**
     * A PSI that contains text that should be spell and grammar checked.
     * <p>
     * Example: the "b" and "old" would be TEXT<br>
     * <code>**b**old</code> is one word "bold"
     */
    TEXT,
    /**
     * A PSI that that <b>WILL</b> be printed, adjacent text represents different words.
     * <p>
     * Example: the {@code ->} would be "SEPARATE"<br>
     * {@code one->two} is two words "one" and "two"
     */
    SEPARATE
  }

  // all tokens that contain full sentences that can be checked for grammar and spelling.
  private static final TokenSet NODES_TO_CHECK = TokenSet.create(
    AsciiDocTokenTypes.LINE_COMMENT,
    AsciiDocTokenTypes.BLOCK_COMMENT,
    AsciiDocTokenTypes.LITERAL_BLOCK,
    AsciiDocTokenTypes.BLOCKREFTEXT,
    AsciiDocElementTypes.SECTION,
    AsciiDocElementTypes.TITLE,
    AsciiDocElementTypes.BLOCK,
    AsciiDocElementTypes.DESCRIPTION_ITEM,
    AsciiDocElementTypes.CELL,
    AsciiDocElementTypes.HEADING
  );

  /** All tokens that contain full sentences that can be checked for grammar and spelling.
   * The contents of these tokens will not be forwarded to the grammar or spell checker.
   * Example: END_OF_SENTENCE is in the Text category, as it needs to be passed to the grammar checker as a ".", "?" or other text,
   * so that the grammar checker recognizes the end of a sentence.
   */
  private static final TokenSet SEPARATOR_TOKENS = TokenSet.create(
    AsciiDocTokenTypes.ARROW,
    AsciiDocTokenTypes.LBRACKET,
    AsciiDocTokenTypes.RBRACKET,
    AsciiDocTokenTypes.LPAREN,
    AsciiDocTokenTypes.RPAREN,
    AsciiDocTokenTypes.DOUBLE_QUOTE,
    AsciiDocTokenTypes.SINGLE_QUOTE,
    AsciiDocTokenTypes.TYPOGRAPHIC_DOUBLE_QUOTE_START,
    AsciiDocTokenTypes.TYPOGRAPHIC_DOUBLE_QUOTE_END,
    AsciiDocTokenTypes.ASSIGNMENT,
    AsciiDocTokenTypes.CELLSEPARATOR,
    AsciiDocTokenTypes.BULLET,
    AsciiDocTokenTypes.ENUMERATION,
    AsciiDocTokenTypes.ADMONITION,
    AsciiDocTokenTypes.CALLOUT
  );


  // all tokens that contain text that is part of a sentence and can be a sub-node of the elements above
  private static final TokenSet TEXT_TOKENS = TokenSet.orSet(TokenSet.create(
    AsciiDocTokenTypes.TEXT,
    AsciiDocTokenTypes.ITALIC,
    AsciiDocTokenTypes.BOLD,
    AsciiDocTokenTypes.BOLDITALIC,
    AsciiDocTokenTypes.MONO,
    AsciiDocTokenTypes.MONOBOLD,
    AsciiDocTokenTypes.DESCRIPTION,
    AsciiDocTokenTypes.DESCRIPTION_END, // for now, keep this as text until it is split into its own root element
    AsciiDocTokenTypes.MACROTEXT,
    AsciiDocTokenTypes.BLOCKREFTEXT,
    AsciiDocTokenTypes.REFTEXT,
    AsciiDocTokenTypes.MONOITALIC,
    AsciiDocTokenTypes.MONOBOLDITALIC,
    AsciiDocTokenTypes.END_OF_SENTENCE,
    AsciiDocTokenTypes.PASSTRHOUGH_CONTENT,
    AsciiDocTokenTypes.LT,
    AsciiDocTokenTypes.GT,
    AsciiDocTokenTypes.TYPOGRAPHIC_SINGLE_QUOTE_START,
    AsciiDocTokenTypes.TYPOGRAPHIC_SINGLE_QUOTE_END,
    AsciiDocTokenTypes.LPAREN,
    AsciiDocTokenTypes.RPAREN,
    AsciiDocTokenTypes.LBRACKET,
    AsciiDocTokenTypes.RBRACKET,
    AsciiDocTokenTypes.BULLET,
    AsciiDocTokenTypes.ATTRIBUTE_VAL, // will only get here if attribute is classified to contain spell checkable content
    AsciiDocTokenTypes.ATTRIBUTE_CONTINUATION,
    AsciiDocTokenTypes.ATTRIBUTE_CONTINUATION_LEGACY,
    // keep the white space in here as blanks are necessary to separate words
    AsciiDocTokenTypes.WHITE_SPACE,
    AsciiDocTokenTypes.WHITE_SPACE_MONO,
    AsciiDocTokenTypes.HEADING_TOKEN,
    AsciiDocTokenTypes.HEADING_OLDSTYLE,
    TokenType.WHITE_SPACE,
    AsciiDocElementTypes.URL, // can nest MACROTEXT, or will show the URL_LINK or URL_EMAIL as default
    AsciiDocElementTypes.REF, // can nest REFTEXT
    AsciiDocElementTypes.LINK, // can nest MACROTEXT
    AsciiDocElementTypes.INLINE_MACRO, // can nest MACROTEXT
    AsciiDocElementTypes.MONO, // will nest MONO
    AsciiDocElementTypes.ITALIC // will nest ITALIC
  ), NODES_TO_CHECK);

  // all tokens are surrounded by spaces, but these spaces are not printed by AsciiDoc
  // and should therefore not be passed to the grammar checker
  private static final TokenSet SPACE_EATING_TOKENS = TokenSet.create(
    AsciiDocTokenTypes.BULLET,
    AsciiDocTokenTypes.ENUMERATION);

  public Behavior getElementBehavior(@NotNull PsiElement root, @NotNull PsiElement child) {
    if (root != child && NODES_TO_CHECK.contains(child.getNode().getElementType())) {
      return Behavior.ABSORB;
    } else if (root == child && child instanceof AsciiDocAttributeDeclarationImpl) {
      if (((AsciiDocAttributeDeclarationImpl) child).hasSpellCheckableContent()) {
        return Behavior.TEXT;
      } else {
        return Behavior.ABSORB;
      }
    } else if (SEPARATOR_TOKENS.contains(child.getNode().getElementType())) {
      return Behavior.SEPARATE;
    } else if (root != child && child instanceof AsciiDocInlineMacro && ((AsciiDocInlineMacro) child).getMacroName().equals("footnote")) {
      return Behavior.ABSORB;
    } else if (spacesIgnoredByAsciiDoc(child)) {
      return Behavior.SEPARATE;
    } else if ((child.getParent() instanceof AsciiDocLink || child.getParent() instanceof AsciiDocUrl)
      // A link or URL can contain either a macro text or no text.
      // AsciiDoc will display the macro text, or the link/email address if no such text is provided.
      // Pass on the content that would be displayed by AsciiDoc to the grammar check.
      && (child.getNode().getElementType() == AsciiDocTokenTypes.URL_LINK || child.getNode().getElementType() == AsciiDocTokenTypes.URL_EMAIL)) {
      boolean macroTextPresent = false;
      ASTNode node = child.getNode();
      while (node != null) {
        if (node.getElementType() == AsciiDocTokenTypes.MACROTEXT) {
          macroTextPresent = true;
          break;
        }
        node = node.getTreeNext();
      }
      if (macroTextPresent) {
        return Behavior.STEALTH;
      } else {
        return Behavior.TEXT;
      }
    } else if (TEXT_TOKENS.contains(child.getNode().getElementType())) {
      return Behavior.TEXT;
    } else {
      return Behavior.STEALTH;
    }
  }

  private boolean spacesIgnoredByAsciiDoc(@NotNull PsiElement child) {
    return child instanceof PsiWhiteSpace && child.getText().matches(" *")
      &&
      ((child.getPrevSibling() != null && SPACE_EATING_TOKENS.contains(child.getPrevSibling().getNode().getElementType()))
        || (child.getNextSibling() != null && SPACE_EATING_TOKENS.contains(child.getNextSibling().getNode().getElementType())));
  }

  public boolean isMyContextRoot(@NotNull PsiElement psiElement) {
    if (psiElement instanceof AsciiDocAttributeDeclarationImpl &&
      ((AsciiDocAttributeDeclarationImpl) psiElement).hasSpellCheckableContent()) {
      return true;
    }
    if (psiElement instanceof AsciiDocInlineMacro &&
      ((AsciiDocInlineMacro) psiElement).getMacroName().equals("footnote")) {
      return true;
    }
    return NODES_TO_CHECK.contains(psiElement.getNode().getElementType())
      || psiElement instanceof PsiComment;
  }

}
