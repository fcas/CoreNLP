package edu.stanford.nlp.trees; 

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Helper class to perform a context-sensitive mapping of PTB POS
 * tags in a tree to universal POS tags.
 *
 * Author: Sebastian Schuster
 * Author: Christopher Manning
 *
 * The original Penn Treebank WSJ contains 45 POS tags (but almost certainly # for British pound currency is a bad idea!)
 *  {#=173, $=9,039, ''=8,658, ,=60,489, -LRB-=1,672, -RRB-=1,689, .=48,733, :=6,087, CC=29,462, CD=44,937, DT=101,190,
 *   EX=1,077, FW=268, IN=121,903, JJ=75,266, JJR=4,042, JJS=2,396, LS=64, MD=11,997, NN=163,935, NNP=114,053,
 *   NNPS=3,087, NNS=73,964, PDT=441, POS=10,801, PRP=21,357, PRP$=10,241, RB=38,197, RBR=2,175, RBS=555, RP=3,275,
 *   SYM=70, TO=27,449, UH=117, VB=32,565, VBD=37,493, VBG=18,239, VBN=24,865, VBP=15,377, VBZ=26,436, WDT=5,323,
 *   WP=2,887, WP$=219, WRB=2,625, ``=8,878}
 *
 * The Web Treebank corpus adds 6 tags, but doesn't have #, yielding 50 POS tags:
 *   ADD, AFX, GW, HYPH, NFP, XX
 *
 * OntoNotes 4.0 has 53 tags. It doesn't have # but adds: -LSB-, -RSB- [both mistakes!], ADD, AFX, CODE, HYPH, NFP,
 *   X [mistake!], XX
 *
 * @author Sebastian Schuster
 */

public class UniversalPOSMapper  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(UniversalPOSMapper.class);

  @SuppressWarnings("WeakerAccess")
  public static final String DEFAULT_TSURGEON_FILE = "edu/stanford/nlp/models/upos/ENUniversalPOS.tsurgeon";

  private static boolean loaded; // = false;

  private static List<Pair<TregexPattern, TsurgeonPattern>> operations; // = null;

  private UniversalPOSMapper() {} // static methods

  public static void load() {
    operations = new ArrayList<>();
    // ------------------------------
    // Context-sensitive mappings
    // ------------------------------

    String [][] toContextMappings = new String [][] {
      // TO -> PART (in CONJP phrases)
      { "@CONJP < TO=target < VB",                 "PART", },
      { "@VP < @VP < (/^TO$/=target <... {/.*/})", "PART", },
      { "@VP <: (/^TO$/=target <... {/.*/})",      "PART", },
      { "TO=target <... {/.*/}",                   "ADP", },   // otherwise TO -> ADP
    };
    for (String[] newOp : toContextMappings) {
      operations.add(new Pair<>(TregexPattern.compile(newOp[0]),
                                Tsurgeon.parseOperation("relabel target " + newOp[1])));

    }

    String [][] otherContextMappings = new String [][] {
      // Don't do this, we are now treating these as copular constructions
      // VB.* -> AUX (for passives where main verb is part of an ADJP)
      // @VP < (/^VB/=target < /^(?i:am|is|are|r|be|being|'s|'re|'m|was|were|been|s|ai|m|art|ar|wase|get|got|getting|gets|gotten)$/ ) < (@ADJP [ < VBN|VBD | < (@VP|ADJP < VBN|VBD) < CC ] )
      //relabel target AUX",

      // VB.* -> AUX (for cases with fronted main VPs)
      { "@SINV < (@VP < (/^VB/=target <  /^(?i:am|is|are|r|be|being|'s|'re|'m|was|were|been|s|ai|m|art|ar|wase)$/ ) $-- (@VP < VBD|VBN))",
        "AUX", },
      // VB.* -> AUX (another, rarer case of fronted VPs)
      { "@SINV < (@VP < (@VP < (/^VB/=target <  /^(?i:am|is|are|r|be|being|'s|'re|'m|was|were|been|s|ai|m|art|ar|wase)$/ )) $-- (@VP < VBD|VBN))",
        "AUX", },

      // VB.* -> AUX (passive, case 2)
      //"%SQ|SINV < (/^VB/=target < /^(?i:am|is|are|r|be|being|'s|'re|'m|was|were|been|s|ai|m|art|ar|wase)$/ $++ (VP < VBD|VBN))",
      //"%relabel target AUX",
      // VB.* -> AUX (active, case 1)
      { "VP < VP < (/^VB.*$/=target <: /^(?i:will|have|can|would|do|is|was|be|are|has|could|should|did|been|may|were|had|'ll|'ve|does|am|might|ca|'m|being|'s|must|'d|'re|wo|shall|get|ve|s|got|r|m|getting|having|d|re|ll|wilt|v|of|my|nt|gets|du|wud|woud|with|willl|wil|wase|shoul|shal|`s|ould|-ll|most|made|hvae|hav|cold|as|art|ai|ar|a)$/)",
        "AUX", },

      // VB -> AUX (active, case 2)
      { "@SQ|SINV < (/^VB/=target $++ /^(?:VP)/ <... {/.*/})", "AUX" },

      // otherwise, VB.* -> VERB
      { "/^VB.*/=target <... {/.*/}", "VERB", },

      // IN -> SCONJ (subordinating conjunctions)
      { "/^SBAR(-[^ ]+)?$/ < (IN=target $++ @S|FRAG|SBAR|SINV <... {/.*/})", "SCONJ", },

      // IN -> SCONJ (subordinating conjunctions II)
      { "@PP < (IN=target $+ @SBAR|S)", "SCONJ" },

      // IN -> ADP (otherwise)
      { "IN=target < __", "ADP" },

      // NN -> SYM (in case of the percent sign)
      { "NN=target <... {/[%]/}", "SYM" },

      // fused det-noun pronouns -> PRON
      { "NN=target < (/^(?i:(somebody|something|someone|anybody|anything|anyone|everybody|everything|everyone|nobody|nothing))$/)",
        "PRON" },

      // NN -> NOUN (otherwise)
      { "NN=target <... {/.*/}", "NOUN" },

      // NFP -> PUNCT (in case of possibly repeated hyphens, asterisks or tildes)
      { "NFP=target <... {/^(~+|\\*+|\\-+)$/}", "PUNCT", },

      // NFP -> SYM (otherwise)
      { "NFP=target <... {/.*/}", "SYM" },

      // RB -> PART when it is verbal negation (not or its reductions)
      { "@VP|SINV|SQ|FRAG|ADVP < (RB=target < /^(?i:not|n't|nt|t|n)$/)", "PART" },

      // Otherwise RB -> ADV
      { "RB=target <... {/.*/}", "ADV" },

      // DT -> PRON (pronominal this/that/these/those)
      { "@NP <: (DT=target < /^(?i:th(is|at|ose|ese))$/)", "PRON", },

      // DT -> DET
      { "DT=target < __", "DET" },

      // WDT -> PRON (pronominal that/which)
      { "@WHNP|NP <: (WDT=target < /^(?i:(that|which))$/)", "PRON" },

      // WDT->SCONJ (incorrectly tagged subordinating conjunctions)
      { "@SBAR < (WDT=target < /^(?i:(that|which))$/)", "SCONJ" },

      // WDT -> DET
      { "WDT=target <... {/.*/}", "DET" },
    };
    for (String[] newOp : otherContextMappings) {
      operations.add(new Pair<>(TregexPattern.compile(newOp[0]),
                                Tsurgeon.parseOperation("relabel target " + newOp[1])));

    }


    String [][] one2oneMappings = new String [][] {
      {"CC", "CCONJ"},
      {"CD", "NUM"},
      {"EX", "PRON"},
      {"FW", "X"},
      {"/^JJ.*$/", "ADJ"},
      {"LS", "X"},
      {"MD", "AUX"},
      {"NNS", "NOUN"},
      {"NNP", "PROPN"},
      {"NNPS", "PROPN"},
      {"PDT", "DET"},
      {"POS", "PART"},
      {"PRP", "PRON"},
      {"/^PRP[$]$/", "PRON"},
      {"RBR", "ADV"},
      {"RBS", "ADV"},
      {"RP", "ADP"},
      {"UH", "INTJ"},
      {"WP", "PRON"},
      {"/^WP[$]$/", "PRON"},
      {"WRB", "ADV"},
      {"/^``$/", "PUNCT"},
      {"/^''$/", "PUNCT"},
      {"/^[()]$/", "PUNCT"},
      {"/^-[RL]RB-$/", "PUNCT"},
      {"/^[,.:]$/", "PUNCT"},
      {"HYPH", "PUNCT"},
      // Also note that there is a no-op rule of SYM -> SYM!
      {"/^[#$]$/", "SYM"},
      {"ADD", "X"},
      {"AFX", "X"},
      {"GW", "X"},
      {"XX", "X"},
    };
    for (String[] newOp : one2oneMappings) {
      operations.add(new Pair<>(TregexPattern.compile(newOp[0] + "=target <: __"),
                                Tsurgeon.parseOperation("relabel target " + newOp[1])));

    }
    loaded = true;
  }

  public static Tree mapTree(Tree t) {
    if ( ! loaded) {
      load();
    }

    if (operations == null) {
      return t;
    }

    return Tsurgeon.processPatternsOnTree(operations, t.deepCopy());
  }

}
