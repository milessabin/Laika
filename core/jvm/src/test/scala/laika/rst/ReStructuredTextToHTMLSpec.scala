/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package laika.rst

import cats.implicits._
import cats.data.NonEmptySet
import laika.api.Transformer
import laika.ast.Path.Root
import laika.ast.{InternalTarget, _}
import laika.file.FileIO
import laika.format.{HTML, ReStructuredText}
import laika.html.TidyHTML
import laika.render.HTMLFormatter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Codec

/**
 * @author Jens Halm
 */
class ReStructuredTextToHTMLSpec extends AnyFlatSpec 
                                 with Matchers {
  
  implicit val codec: Codec = Codec.UTF8
    
  /** Uses JTidy to remove cosmetic differences between Laika and reStructuredText output,
   *  plus a few additional, manual cleaning operations for purely cosmetic differences
   *  not covered by JTidy.
   */
  def tidyAndAdjust (html: String): String = {
    val prepared = html
      .replace("\r\n", "\n")
      .replace("\r", "\n")
      .replaceAll("([^\n])</", "$1\n</") // rst often inserts a new line before a closing tag
      .replace("$Revision: 7629 $","7629").replace("$Date: 2013-03-11 21:01:03 +0000 (Mon, 11 Mar 2013) $","2013-03-11") // RCS field substitution not supported
      .replace("""class="field-list"""", """class="docinfo"""").replace("""class="field-name"""","""class="docinfo-name"""").replace("""class="field-body"""","""class="docinfo-content"""") // docinfo field lists deferred to 0.4
    
    TidyHTML(prepared)
      .replace("<col></colgroup>", "<col>\n</colgroup>") // fix for JTidy oddity
      .replace(">\n\n<",">\n<") // rst often adds blank lines between tags
      .replace("§§§§","&mdash;") // JTidy converts it back to utf-8 char otherwise
      .trim
  }
  

  def transformAndCompare (name: String): Unit = {
    val noValidation =
      """{%
        |laika.links.excludeFromValidation = ["/"] 
        |%}
        |""".stripMargin
    
    val path = FileIO.classPathResourcePath("/rstSpec") + "/" + name
    val input = noValidation + FileIO.readFile(path + ".rst")

    def quotedBlockContent (content: Seq[Block], attr: Seq[Span]) =
      if (attr.isEmpty) content
      else content :+ Paragraph(RawContent(NonEmptySet.one("html"), "§§§§") +: attr, Style.attribution)

    def renderBlocks (fmt: HTMLFormatter, tagName: String, options: Options, content: Seq[Block], attrs: (String, String)*): String = content match {
      case Seq(ss: SpanSequence)     => fmt.element(tagName, options, Seq(ss), attrs: _*)
      case Seq(Paragraph(spans,opt)) => fmt.element(tagName, options, Seq(SpanSequence(spans,opt)), attrs: _*)
      case other                     => fmt.indentedElement(tagName, options, other, attrs: _*)
    }
    
    def dropLaikaPrefix(id: String): String = if (id.startsWith("__")) id.drop(5) else id
    
    /* This set of renderer overrides adjust Laika's generic HTML renderer to the specific output generated by the reference implementation.
       These adjustments are not integrated into the Laika's core as it would be complicated overhead to have different renderers per
       input format, in particular in case where more than one markup format is used as the input.
     */
    val actual = Transformer
      .from(ReStructuredText)
      .to(HTML)
      .rendering { 
        case (fmt, Emphasized(content,opt)) if opt.styles.contains("title-reference") => fmt.element("cite", NoOpt, content) 
        case (fmt, sl@ SpanLink(content, target, title, opt)) => target match {
          case ExternalTarget(url) =>
            fmt.element("a", opt + Styles("reference","external"), content, fmt.optAttributes("href" -> Some(url), "title" -> title):_*)
          case it: InternalTarget =>
            val relativePath = it.relativeTo(fmt.path).relativePath
            // rst makes a different kind of distinction between external and internal links, so we adjust Laika's renderers just for this test
            if (relativePath.suffix.contains("html") || relativePath.fragment.isEmpty) fmt.child(sl.copy(target = ExternalTarget(relativePath.toString)))
            else fmt.element("a", opt + Styles("reference","internal"), content, fmt.optAttributes("href" -> Some("#"+relativePath.fragment.get), "title"->title):_*)
        }
        case (fmt, LiteralBlock(content,opt))     => fmt.withoutIndentation(_.textElement("pre", opt + Styles("literal-block"), content))
        case (fmt, Literal(content,opt))          => fmt.withoutIndentation(_.textElement("tt", opt + Styles("docutils","literal"), content))
        case (fmt, FootnoteLink(id,label,opt))    => fmt.textElement("a", opt + Styles("footnote-reference"), s"[$label]", "href"-> ("#"+dropLaikaPrefix(id))) 
        case (fmt, f: Footnote) if f.options.id.exists(_.startsWith("__")) => fmt.child(f.withId(dropLaikaPrefix(f.options.id.get)))
        case (fmt, Section(header, content, opt)) => fmt.indentedElement("div", opt+Id(header.options.id.getOrElse(""))+(if(header.level == 1) Styles("document") else Style.section), header +: content)
        case (fmt, Header(level, (it: InternalLinkTarget) :: rest, opt)) => fmt.childPerLine(Seq(it, Header(level, rest, opt))) // move target out of the header content
        case (fmt, Header(level, content, opt))   => fmt.element("h" + (level-1), NoOpt, content) // rst special treatment of first header
        case (fmt, Title(content, opt))           => fmt.element("h1", NoOpt, content, "class" -> "title")
        case (fmt, TitledBlock(title, content, opt)) => fmt.indentedElement("div", opt, Paragraph(title,Styles("admonition-title")) +: content)
        case (fmt, QuotedBlock(content,attr,opt)) => renderBlocks(fmt, "blockquote", opt, quotedBlockContent(content,attr))
        case (fmt, InternalLinkTarget(opt))       => fmt.textElement("span", opt, "")
        case (_, i: InvalidBlock)                 => ""
     }
      .failOnMessages(MessageFilter.None)
      .build.transform(input, Root / "doc")
    
    val expected = FileIO.readFile(path + "-tidy.html")
    tidyAndAdjust(actual.toOption.get) should be (expected)
  }
  
  
  "The adjusted transformer for reStructuredText" should "transform the reStructuredText specification to HTML equivalent to the output of the reference parser" in {
    transformAndCompare("rst-spec")
  }
  
  
}
