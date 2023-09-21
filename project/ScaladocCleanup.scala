import java.io.File

object ScaladocCleanup {

  def removeUnwantedEntries(
      mappings: Seq[(File, String)],
      basePath: sbt.File
  ): Seq[(File, String)] = {

    // remove unwanted folders
    val filtered = mappings.filterNot { case (_, mapping) =>
      mapping.startsWith("org") || mapping.startsWith("sbt") || mapping.startsWith("scodec")
    }

    // replace root index to remove unwanted entries from navigation
    filtered.map { case (file, mapping) =>
      if (mapping == "index.html") (new File(basePath, "docs/api/index.html"), mapping)
      else (file, mapping)
    }

  }

}
