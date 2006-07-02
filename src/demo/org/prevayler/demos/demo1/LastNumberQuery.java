// Prevayler, The Free-Software Prevalence Layer
// Copyright 2001-2006 by Klaus Wuestefeld
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.
//
// Prevayler is a trademark of Klaus Wuestefeld.
// See the LICENSE file for license details.

package org.prevayler.demos.demo1;

import org.prevayler.GenericTransaction;
import org.prevayler.PrevalenceContext;
import org.prevayler.ReadOnly;

@ReadOnly public class LastNumberQuery implements GenericTransaction<NumberKeeper, Integer, RuntimeException> {

    public Integer executeOn(NumberKeeper prevalentSystem, @SuppressWarnings("unused") PrevalenceContext prevalenceContext) {
        return prevalentSystem.lastNumber();
    }

}
