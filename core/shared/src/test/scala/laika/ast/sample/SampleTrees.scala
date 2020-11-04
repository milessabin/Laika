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

package laika.ast.sample

import laika.ast.Path.Root
import laika.ast.{Block, Document, DocumentCursor, DocumentTree, DocumentTreeRoot, Header, Id, Paragraph, Path, RootElement, Section, StaticDocument, Style, Styles, TemplateDocument, TemplateRoot, TemplateSpan, Text, Title, TreeContent}
import laika.config.{Config, ConfigBuilder, LaikaKeys, Origin}

object SampleTrees {

  def twoDocuments: SampleRoot = ???
  def sixDocuments: SampleSixDocuments = new SampleSixDocuments(SampleRoot(2, 6, SampleContent.text))
  
}

sealed trait BuilderKey {
  def num: Int
  def defaultTitle: String
}
object BuilderKey {
  case class Doc(num: Int) extends BuilderKey {
    val defaultTitle = "Title " + num // TODO - should be Doc
  }
  case class Tree(num: Int) extends BuilderKey {
    val defaultTitle = "Tree " + num
  }
}

trait SampleOps { self =>
  
  type RootApi <: SampleOps
  
  protected def sampleRoot: SampleRoot

  trait DocOps extends SampleDocumentOps {
    type RootApi <: self.RootApi
    protected def sampleRoot = self.sampleRoot
    private[sample] def copyWith (root: SampleRoot): RootApi = self.copyWith(root).asInstanceOf[RootApi]
  }
  trait TreeOps extends SampleTreeOps {
    type RootApi <: self.RootApi
    protected def sampleRoot = self.sampleRoot
    private[sample] def copyWith (root: SampleRoot): RootApi = self.copyWith(root).asInstanceOf[RootApi]
  }
  def suffix (value: String): RootApi = copyWith(sampleRoot.copy(suffix = Some(value)))
  def docContent (f: BuilderKey => Seq[Block]): RootApi = copyWith(sampleRoot.copy(defaultContent = f))
  def docContent (blocks: Seq[Block]): RootApi = copyWith(sampleRoot.copy(defaultContent = _ => blocks))
  def docContent (block: Block, blocks: Block*): RootApi = docContent(block +: blocks)

  def staticDoc (path: Path): RootApi =
    copyWith(sampleRoot.copy(
      staticDocuments = sampleRoot.staticDocuments :+ StaticDocument(path)
    ))

  def staticDoc (path: Path, format: String, formats: String*): RootApi =
    copyWith(sampleRoot.copy(
      staticDocuments = sampleRoot.staticDocuments :+ StaticDocument(path, format, formats:_*)
    ))
  
  protected def buildTree (treeNum: Int, docNum: Int, parentConfig: Config, singleDoc: Boolean = false): DocumentTree = {
    val docs = sampleRoot.docBuilders(docNum) +: (if (singleDoc) Seq() else Seq(sampleRoot.docBuilders(docNum + 1)))
    sampleRoot.treeBuilders(treeNum).build(docs, parentConfig, sampleRoot)
  }
  
  private[sample] def addDocumentConfig (key: BuilderKey, f: ConfigBuilder => ConfigBuilder): RootApi =
    updateDocument(key, target => target.copy(config = f +: target.config))

  private[sample] def setDocumentContent (key: BuilderKey, f: BuilderKey => Seq[Block]): RootApi =
    updateDocument(key, target => target.copy(content = Some(f)))

  private def updateDocument (key: BuilderKey, f: SampleDocument => SampleDocument): RootApi = {
    key match {
      case _: BuilderKey.Doc =>
        val target = sampleRoot.docBuilders(key.num - 1)
        copyWith(sampleRoot.copy(docBuilders = sampleRoot.docBuilders.updated(key.num - 1, f(target))))
        
      case _: BuilderKey.Tree =>
        val tree = sampleRoot.treeBuilders(key.num)
        val target = tree.titleDoc.getOrElse(SampleDocument(key))
        copyWith(sampleRoot.copy(treeBuilders =
          sampleRoot.treeBuilders.updated(key.num, tree.copy(titleDoc = Some(f(target))))
        ))
    }
  }
  def titleDocuments (includeRoot: Boolean = true): RootApi = {
    val start = if (includeRoot) 0 else 1
    (start to 2).map(BuilderKey.Tree).foldLeft(copyWith(sampleRoot)) {
      case (api, key) => api.updateDocument(key, identity).asInstanceOf[RootApi]
    }
  }

  private[sample] def addTreeConfig (key: BuilderKey, f: ConfigBuilder => ConfigBuilder): RootApi =
    updateTree(key, target => target.copy(config = f +: target.config))

  private[sample] def addTemplate (key: BuilderKey, name: String, spans: Seq[TemplateSpan]): RootApi = {
    updateTree(key, target => {
      val doc = TemplateDocument(target.path / name, TemplateRoot(spans))
      target.copy(templates = doc +: target.templates)
    })
  }
  private def updateTree (key: BuilderKey, f: SampleTree => SampleTree): RootApi = {
    val target = sampleRoot.treeBuilders(key.num)
    copyWith(sampleRoot.copy(treeBuilders = sampleRoot.treeBuilders.updated(key.num, f(target))))
  }
  
  private[sample] def copyWith (root: SampleRoot): RootApi
  
}

trait NumberedSampleOps extends SampleOps {

  protected def key: BuilderKey 
  
}

class SampleOneDocument (protected val sampleRoot: SampleRoot) extends SampleOps { self =>

  type RootApi = SampleOneDocument

  object root extends TreeOps { type RootApi = SampleOneDocument; protected def key = BuilderKey.Tree(0) }
  object doc1 extends DocOps { type RootApi = SampleOneDocument; protected def key = BuilderKey.Doc(1) }

  def config (f: ConfigBuilder => ConfigBuilder): RootApi = root.config(f)
  def apply (f: SampleOneDocument => SampleOneDocument): RootApi = f(this)
  private[sample] def copyWith (root: SampleRoot): RootApi = new SampleOneDocument(root)

  def build: DocumentTreeRoot = DocumentTreeRoot(
    tree = buildTree(0, 0, Config.empty, singleDoc = true),
    staticDocuments = sampleRoot.staticDocuments
  )
}

class SampleSixDocuments (protected val sampleRoot: SampleRoot) extends SampleOps { self =>

  import BuilderKey._
  
  type RootApi = SampleSixDocuments
  
  object root  extends TreeOps { 
    type RootApi = SampleSixDocuments
    protected def key = Tree(0)
    object titleDoc extends DocOps { type RootApi = SampleSixDocuments; protected def key = Tree(0) } 
  }
  object tree1 extends TreeOps {
    type RootApi = SampleSixDocuments
    protected def key = Tree(1)
    object titleDoc extends DocOps { type RootApi = SampleSixDocuments; protected def key = Tree(1) }
  }
  object tree2 extends TreeOps { 
    type RootApi = SampleSixDocuments
    protected def key = Tree(2)
    object titleDoc extends DocOps { type RootApi = SampleSixDocuments; protected def key = Tree(2) }
  }
  
  object doc1 extends DocOps { type RootApi = SampleSixDocuments; protected def key = Doc(1) }
  object doc2 extends DocOps { type RootApi = SampleSixDocuments; protected def key = Doc(2) }
  object doc3 extends DocOps { type RootApi = SampleSixDocuments; protected def key = Doc(3) }
  object doc4 extends DocOps { type RootApi = SampleSixDocuments; protected def key = Doc(4) }
  object doc5 extends DocOps { type RootApi = SampleSixDocuments; protected def key = Doc(5) }
  object doc6 extends DocOps { type RootApi = SampleSixDocuments; protected def key = Doc(6) }
  
  def config (f: ConfigBuilder => ConfigBuilder): RootApi = root.config(f)
  def apply (f: SampleSixDocuments => SampleSixDocuments): RootApi = f(this)
  private[sample] def copyWith (root: SampleRoot): RootApi = new SampleSixDocuments(root)
  
  def build: DocumentTreeRoot = {
    val tree = buildTree(0, 0, Config.empty)
    DocumentTreeRoot(
      tree = tree.copy(
        content = tree.content ++ Seq(buildTree(1, 2, tree.config), buildTree(2, 4, tree.config))
      ),
      staticDocuments = sampleRoot.staticDocuments
    )
  }
}

private[sample] case class SampleRoot (treeBuilders: IndexedSeq[SampleTree],
                                       docBuilders: IndexedSeq[SampleDocument],
                                       defaultContent: BuilderKey => Seq[Block],
                                       suffix: Option[String] = None,
                                       staticDocuments: Seq[StaticDocument] = Nil) {
  
}

object SampleRoot {
  private[sample] def apply(numTrees: Int, numDocs: Int, defaultContent: BuilderKey => Seq[Block]): SampleRoot =
    new SampleRoot(
      (0 to numTrees).map(num => SampleTree(BuilderKey.Tree(num))).toVector, 
      (1 to numDocs).map(num => SampleDocument(BuilderKey.Doc(num))).toVector,
      SampleContent.text
    )
}

trait SampleDocumentOps extends NumberedSampleOps {
  
  def config (f: ConfigBuilder => ConfigBuilder): RootApi = addDocumentConfig(key, f)

  // TODO - configValue API

  def content (f: BuilderKey => Seq[Block]): RootApi = setDocumentContent(key, f)
  def content (blocks: Seq[Block]): RootApi = setDocumentContent(key, _ => blocks)
  def content (block: Block, blocks: Block*): RootApi = content(block +: blocks)
  
  def buildCursor: DocumentCursor = ???
  
}

trait SampleTreeOps extends NumberedSampleOps {

  def config (f: ConfigBuilder => ConfigBuilder): RootApi = addTreeConfig(key, f)
  def template (name: String, spans: Seq[TemplateSpan]): RootApi = addTemplate(key, name, spans)
  def template (name: String, span: TemplateSpan, spans: TemplateSpan*): RootApi = template(name, span +: spans)
  
}

private[sample] case class SampleDocument (key: BuilderKey, 
                                           config: Seq[ConfigBuilder => ConfigBuilder] = Nil, 
                                           content: Option[BuilderKey => Seq[Block]] = None) {

  private[sample] def build (treePath: Path, parentConfig: Config, root: SampleRoot): Document = {
    val suffix = root.suffix.fold("")("." + _)
    val localName = key match {
      case _: BuilderKey.Tree => "README"
      case _: BuilderKey.Doc  => "doc-" + key.num
    } 
    val path = treePath / (localName + suffix)
    val configBuilder = ConfigBuilder.withFallback(parentConfig, Origin(Origin.DocumentScope, path))
    Document(
      path, 
      RootElement(content.getOrElse(root.defaultContent)(key)),
      config = config.foldLeft(configBuilder){ case (builder, f) => f(builder) }.build
    )
  }
}

private[sample] case class SampleTree (key: BuilderKey,
                                       config: Seq[ConfigBuilder => ConfigBuilder] = Nil,
                                       templates: Seq[TemplateDocument] = Nil,
                                       titleDoc: Option[SampleDocument] = None) {

  def versioned: SampleTree = ???
  def unversioned: SampleTree = ???
  
  val path = if (key.num == 0) Root else Root / s"tree-${key.num}"

  private[sample] def build (content: Seq[SampleDocument], parentConfig: Config, root: SampleRoot): DocumentTree = {
    val configBuilder = ConfigBuilder.withFallback(parentConfig, Origin(Origin.TreeScope, path))
    val conf = config.foldLeft(configBuilder){ case (builder, f) => f(builder) }.build
    DocumentTree(
      path,
      content.map(_.build(path, conf, root)),
      titleDocument = titleDoc.map(_.build(path, conf, root)),
      templates = templates,
      config = conf
    )
  }

}

object SampleContent {

  private def header (level: Int, pos: Int, style: String = "section") =
    Header(level, List(Text("Section "+pos)), Styles(style))
    
  private def section (level: Int, pos: Int, content: Seq[Block] = Nil): Section =
    Section(header(level, pos).withId(s"section-$pos"), content)
  
  val empty: BuilderKey => Seq[Block] = _ => Nil
  val text: BuilderKey => Seq[Block] = key => Seq(Paragraph(s"Text ${key.num}"))
  val title: BuilderKey => Seq[Block] = key => Seq(Title(key.defaultTitle))
  val fourHeaders: BuilderKey => Seq[Block] = key => Seq(
    Header(1, List(Text(key.defaultTitle)), Style.title),
      header(1,1),
      header(2,2),
      header(1,3),
      header(2,4)
    )
  val fourSections: BuilderKey => Seq[Block] = key => Seq(
    Title(List(Text(key.defaultTitle)), Id("title") + Style.title),
    section(1, 1, Seq(section(2,2))),
    section(1, 3, Seq(section(2,4)))
  )

}

object SampleConfig {
  
  val noLinkValidation: ConfigBuilder => ConfigBuilder = _.withValue(LaikaKeys.validateLinks, false)
  def targetFormats (formats: String*): ConfigBuilder => ConfigBuilder = _.withValue(LaikaKeys.targetFormats, formats)
  def siteBaseURL (value: String): ConfigBuilder => ConfigBuilder = _.withValue(LaikaKeys.siteBaseURL, value)
  def title (text: String): ConfigBuilder => ConfigBuilder = _.withValue(LaikaKeys.title, text)
  
//  val versions: ConfigBuilder => ConfigBuilder = ???
//  val selections: ConfigBuilder => ConfigBuilder = ???
  
}
