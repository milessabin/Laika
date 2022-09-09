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

package laika.helium.generate

import laika.ast.Path.Root
import laika.ast._
import laika.config.ConfigEncoder.ObjectBuilder
import laika.config._
import laika.helium.Helium
import laika.helium.config._

private[laika] object ConfigGenerator {

  implicit val releaseEncoder: ConfigEncoder[ReleaseInfo] = ConfigEncoder[ReleaseInfo] { releaseInfo =>
    ConfigEncoder.ObjectBuilder.empty
      .withValue("title", releaseInfo.title)
      .withValue("version", releaseInfo.version)
      .build
  }

  implicit val teaserEncoder: ConfigEncoder[Teaser] = ConfigEncoder[Teaser] { teaser =>
    ConfigEncoder.ObjectBuilder.empty
      .withValue("title", teaser.title)
      .withValue("description", teaser.description)
      .build
  }
  
  private def buildTeaserRows (teasers: Seq[Teaser]): Seq[ObjectValue] = if (teasers.isEmpty) Nil else
    BalancedGroups.create(teasers.toVector, Math.ceil(teasers.size.toDouble / 3).toInt).map { row =>
      ObjectBuilder.empty.withValue("teasers", row).build
    }

  implicit val landingPageEncoder: ConfigEncoder[LandingPage] = ConfigEncoder[LandingPage] { landingPage =>
    ConfigEncoder.ObjectBuilder.empty
      .withValue("logo", landingPage.logo)
      .withValue("title", landingPage.title)
      .withValue("subtitle", landingPage.subtitle)
      .withValue("latestReleases", landingPage.latestReleases)
      .withValue("license", landingPage.license)
      .withValue("documentationLinks", landingPage.documentationLinks)
      .withValue("projectLinks", landingPage.projectLinks)
      .withValue("teaserRows", buildTeaserRows(landingPage.teasers))
      .build
  }

  implicit val topNavBarEncoder: ConfigEncoder[TopNavigationBar] = ConfigEncoder[TopNavigationBar] { navBar =>
    ConfigEncoder.ObjectBuilder.empty
      .withValue("home", navBar.homeLink)
      .withValue("links", navBar.navLinks)
      .withValue("phoneLinks", navBar.navLinks.collect { case s: ThemeLinkSpan => s })
      .withValue("versionMenu", navBar.versionMenu)
      .build
  }

  implicit val mainNavEncoder: ConfigEncoder[MainNavigation] = ConfigEncoder[MainNavigation] { mainNav =>
    def encodeLink (link: TextLink): ConfigValue = ConfigEncoder.ObjectBuilder.empty
      .withValue("target", link.target.render())
      .withValue("title", link.text)
      .withValue("depth", 1)
      .build
    def encodeSection (section: ThemeNavigationSection): ConfigValue = ConfigEncoder.ObjectBuilder.empty
      .withValue("title", section.title)
      .withValue("entries", section.links.map(encodeLink).toList)
      .build
    ConfigEncoder.ObjectBuilder.empty
      .withValue("depth", mainNav.depth)
      .withValue("excludeSections", !mainNav.includePageSections)
      .withValue("prependLinks", mainNav.prependLinks.map(encodeSection))
      .withValue("appendLinks", mainNav.appendLinks.map(encodeSection))
      .build
  }

  implicit val pageNavEncoder: ConfigEncoder[PageNavigation] = ConfigEncoder[PageNavigation] { pageNav =>
    ConfigEncoder.ObjectBuilder.empty
      .withValue("enabled", pageNav.enabled)
      .withValue("depth", pageNav.depth)
      .withValue("sourceBaseURL", pageNav.sourceBaseURL.map(_.stripSuffix("/")))
      .withValue("sourceLinkText", pageNav.sourceLinkText)
      .withValue("sourceLinkIcon", HeliumIcon.edit)
      .build
  }

  implicit val pdfLayoutEncoder: ConfigEncoder[PDFLayout] = ConfigEncoder[PDFLayout] { layout =>
    ConfigEncoder.ObjectBuilder.empty
      .withValue("pageHeight", layout.pageHeight.displayValue)
      .withValue("pageWidth", layout.pageWidth.displayValue)
      .withValue("marginTop", layout.marginTop.displayValue)
      .withValue("marginBottom", layout.marginBottom.displayValue)
      .withValue("marginLeft", layout.marginLeft.displayValue)
      .withValue("marginRight", layout.marginRight.displayValue)
      .build
  }

  implicit val themeFontsEncoder: ConfigEncoder[ThemeFonts] = ConfigEncoder[ThemeFonts] { fonts =>
    ConfigEncoder.ObjectBuilder.empty
      .withValue("headlines", fonts.headlines)
      .withValue("body", fonts.body)
      .withValue("code", fonts.code)
      .build
  }
  
  implicit val favIconEncoder: ConfigEncoder[Favicon] = ConfigEncoder[Favicon] { icon =>
    ConfigEncoder.ObjectBuilder.empty
      .withValue("target", icon.target.render())
      .withValue("sizes", icon.sizes)
      .withValue("type", icon.mediaType)
      .build
  }
  
  private val templatePaths: Map[String, Path] = {
    Seq("head", "topNav", "mainNav", "pageNav", "footer").map { name =>
      name -> Root / "helium" / "templates" / s"$name.template.html"
    }.toMap
  }

  def populateConfig (helium: Helium): Config =
    ConfigBuilder.empty
      .withValue("helium.landingPage", helium.siteSettings.landingPage)
      .withValue("helium.topBar", helium.siteSettings.layout.topNavigationBar)
      .withValue("helium.site.mainNavigation", helium.siteSettings.layout.mainNavigation)
      .withValue("helium.site.pageNavigation", helium.siteSettings.layout.pageNavigation)
      .withValue("helium.favIcons", helium.siteSettings.layout.favIcons)
      .withValue("helium.site.templates", templatePaths)
      .withValue("laika.site.metadata", helium.siteSettings.metadata)
      .withValue("laika.epub", helium.epubSettings.bookConfig)
      .withValue("laika.pdf", helium.pdfSettings.bookConfig)
      .withValue("helium.pdf", helium.pdfSettings.layout)
      .withValue("helium.webFonts", helium.siteSettings.fontResources.flatMap { _.resource.webCSS })
      .withValue("helium.site.includeEPUB", helium.siteSettings.layout.downloadPage.fold(false)(_.includeEPUB))
      .withValue("helium.site.includePDF", helium.siteSettings.layout.downloadPage.fold(false)(_.includePDF))
      .withValue(LaikaKeys.site.css.child("globalSearchPaths"), (Root / "helium") +: helium.siteSettings.htmlIncludes.includeCSS)
      .withValue(LaikaKeys.site.js.child("globalSearchPaths"), (Root / "helium") +: helium.siteSettings.htmlIncludes.includeJS)
      .withValue(LaikaKeys.epub.css.child("globalSearchPaths"), (Root / "helium") +: helium.epubSettings.htmlIncludes.includeCSS)
      .withValue(LaikaKeys.epub.js.child("globalSearchPaths"), (Root / "helium") +: helium.epubSettings.htmlIncludes.includeJS)
      .withValue("helium.site.fontFamilies", helium.siteSettings.themeFonts)
      .withValue("helium.epub.fontFamilies", helium.epubSettings.themeFonts)
      .withValue("helium.pdf.fontFamilies", helium.pdfSettings.themeFonts)
      .withValue(LaikaKeys.siteBaseURL, helium.siteSettings.baseURL)
      .withValue(LaikaKeys.versions, helium.siteSettings.versions)
      .withValue("laika.pdf.coverImages", helium.pdfSettings.coverImages)
      .withValue("laika.epub.coverImages", helium.epubSettings.coverImages)
      .withValue("laika.pdf.coverImage", helium.pdfSettings.coverImages.find(_.classifier.isEmpty).map(_.path))
      .withValue("laika.epub.coverImage", helium.epubSettings.coverImages.find(_.classifier.isEmpty).map(_.path))
      .withValue(HeliumIcon.registry)
      .build
  
}

/** Utility for splitting a collection into a balanced group of items as opposed to the unbalanced 
  * `grouped` method of the Scala collection API.
  */
object BalancedGroups {

  /** Creates a balanced group of items based on the given desired size.
    */
  def create[A] (items: Vector[A], size: Int): Vector[Vector[A]] = {
    val mod = items.size % size
    val loSize = items.size / size
    val hiSize = loSize + 1
    val (hi,lo) = items.splitAt(mod * hiSize)
    val hiBatch = if (mod > 0)    hi.grouped(hiSize) else Vector()
    val loBatch = if (loSize > 0) lo.grouped(loSize) else Vector()
    hiBatch.toVector ++ loBatch.toVector
  }

}
