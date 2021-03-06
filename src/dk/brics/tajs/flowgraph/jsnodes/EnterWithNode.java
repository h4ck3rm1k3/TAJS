/*
 * Copyright 2012 Aarhus University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.brics.tajs.flowgraph.jsnodes;

import dk.brics.tajs.flowgraph.BasicBlock;
import dk.brics.tajs.flowgraph.SourceLocation;
import dk.brics.tajs.util.AnalysisException;

/**
 * Enter-with node.
 * <p>
 * with(<i>v</i>) { ... }
 */
public class EnterWithNode extends Node {

	private int object_reg;

	/**
	 * Constructs a new enter-with node.
	 */
	public EnterWithNode(int object_reg, SourceLocation location) {
		super(location);
		this.object_reg = object_reg;
	}

	/**
	 * Returns the object register.
	 */
	public int getObjectRegister() {
		return object_reg;
	}

    /**
     * Sets the object register.
     */
    public void setObjectRegister(int object_reg) {
        this.object_reg = object_reg;
    }
	
	@Override
	public String toString() {
		return "enter-with[v" + object_reg + "]";
	}

	@Override
	public <ArgType> void visitBy(NodeVisitor<ArgType> v, ArgType a) {
		v.visit(this, a);
	}

	@Override
	public boolean canThrowExceptions() {
		return true;
	}

    @Override
    public void check(BasicBlock b) {
        if (object_reg == NO_VALUE)
            throw new AnalysisException("Invalid object register: " + toString());
    }
}
