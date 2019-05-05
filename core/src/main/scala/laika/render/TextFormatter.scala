/*
 * Copyright 2012-2019 the original author or authors.
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

package laika.render

import laika.ast._
import laika.factory.RenderContext2


/** API for renderers that produce text output.
 * 
 * @param renderChild the function to use for rendering child elements
 * @param elementStack the stack of parent elements of this formatter in recursive rendering
 * @param indentation the level of indentation for this formatter
 *                   
 *  @author Jens Halm
 */
case class TextFormatter (renderChild: (TextFormatter, Element) => String,
                          elementStack: List[Element],
                          indentation: Indentation) extends BaseFormatter[TextFormatter](renderChild, elementStack, indentation, MessageLevel.Debug) {

  protected def withChild (element: Element): TextFormatter = copy(elementStack = element :: elementStack)

  protected def withIndentation (newIndentation: Indentation): TextFormatter = copy(indentation = newIndentation)
  
}

object TextFormatter extends (RenderContext2[TextFormatter] => TextFormatter) {
  def apply (context: RenderContext2[TextFormatter]): TextFormatter = 
    TextFormatter(context.renderChild, List(context.root), context.indentation)
}
