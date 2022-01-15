/*
 * Copyright 2014-2022 TNG Technology Consulting GmbH
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
package com.tngtech.archunit.core.importer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class DependencyResolutionProcess {
    private final Set<String> typeNames = new HashSet<>();
    private boolean initializationComplete = false;

    void registerMemberType(String typeName) {
        if (!initializationComplete) {
            typeNames.add(typeName);
        }
    }

    public void registerMemberTypes(Collection<String> typeNames) {
        for (String typeName : typeNames) {
            registerMemberType(typeName);
        }
    }

    public void resolve(ImportedClasses importedClasses) {
        initializationComplete = true;
        for (String typeName : typeNames) {
            importedClasses.ensurePresent(typeName);
        }
    }
}
