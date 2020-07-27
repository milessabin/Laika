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

package laika.io.binary

import cats.effect.Sync
import laika.api.builder.{OperationConfig, TwoPhaseRenderer}
import laika.ast.DocumentTreeRoot
import laika.factory.BinaryPostProcessor
import laika.io.binary.ParallelRenderer.BinaryRenderer
import laika.io.descriptor.RendererDescriptor
import laika.io.model.{BinaryInput, BinaryOutput}
import laika.io.ops.BinaryOutputOps
import laika.io.runtime.{RendererRuntime, Runtime}
import laika.io.theme.Theme

/** Renderer that merges a tree of input documents to a single binary output document.
  *
  * @author Jens Halm
  */
class ParallelRenderer[F[_]: Sync: Runtime] (renderer: BinaryRenderer, theme: Theme[F]) {

  /** Builder step that specifies the root of the document tree to render.
    */
  def from (input: DocumentTreeRoot): ParallelRenderer.OutputOps[F] =
    ParallelRenderer.OutputOps(renderer, theme, input, Nil)

}

/** Builder API for constructing a rendering operation for a tree of binary output documents.
  */
object ParallelRenderer {

  type BinaryRenderer = TwoPhaseRenderer[BinaryPostProcessor]

  /** Builder step that allows to specify the execution context
    * for blocking IO and CPU-bound tasks.
    */
  case class Builder[F[_]: Sync: Runtime] (renderer: BinaryRenderer, theme: Theme[F]) {

    /** Applies the specified theme to this renderer, overriding any previously specified themes.
      */
    def withTheme (theme: Theme[F]): Builder[F] = copy(theme = theme)
    
    /** Final builder step that creates a parallel renderer for binary output.
      */
    def build: ParallelRenderer[F] = new ParallelRenderer[F](renderer, theme)
    
  }

  /** Builder step that allows to specify the output to render to.
    */
  case class OutputOps[F[_]: Sync: Runtime] (renderer: BinaryRenderer,
                                              theme: Theme[F],
                                              input: DocumentTreeRoot,
                                              staticDocuments: Seq[BinaryInput[F]]) extends BinaryOutputOps[F] {

    val F: Sync[F] = Sync[F]

    type Result = Op[F]

    /** Copies the specified binary input to the output target,
      * in addition to rendering the document tree.
      */
    def copying (toCopy: Seq[BinaryInput[F]]): OutputOps[F] = copy(staticDocuments = staticDocuments ++ toCopy)

    def toOutput (output: BinaryOutput[F]): Op[F] = Op[F](renderer, theme, input, output, staticDocuments)

  }

  /** Represents a rendering operation for a tree of documents merged into a single binary output document.
    *
    * It can be run by invoking the `render` method which delegates to the library's
    * default runtime implementation or by developing a custom runner that performs
    * the rendering based on this operation's properties.
    */
  case class Op[F[_]: Sync: Runtime] (renderer: BinaryRenderer,
                                       theme: Theme[F],
                                       input: DocumentTreeRoot, 
                                       output: BinaryOutput[F], 
                                       staticDocuments: Seq[BinaryInput[F]] = Nil) {

    /** The configuration of the renderer for the interim format.
      */
    val config: OperationConfig = renderer.interimRenderer.config.withBundles(theme.extensions)

    /** Performs the rendering operation based on the library's
      * default runtime implementation, suspended in the effect F.
      */
    def render: F[Unit] = RendererRuntime.run(this)

    /** Provides a description of this operation, the renderers
      * and extension bundles used, as well as the output target.
      * This functionality is mostly intended for tooling support.
      */
    def describe: F[RendererDescriptor] = RendererDescriptor.create(this)
  }

}