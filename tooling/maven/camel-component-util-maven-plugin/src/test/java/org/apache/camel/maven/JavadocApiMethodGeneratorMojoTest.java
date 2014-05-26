/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.maven;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Test;

/**
 * Tests {@link JavadocApiMethodGeneratorMojo}
 */
public class JavadocApiMethodGeneratorMojoTest extends AbstractGeneratorMojoTest {

    @Test
    public void testExecute() throws IOException, MojoFailureException, MojoExecutionException {

        // delete target file to begin
        final File outFile = new File(OUT_DIR, PACKAGE_PATH + "VelocityEngineApiMethod.java");
        if (outFile.exists()) {
            outFile.delete();
        }

        final JavadocApiMethodGeneratorMojo mojo = new JavadocApiMethodGeneratorMojo();

        mojo.outDir = new File(OUT_DIR);
        mojo.outPackage = AbstractGeneratorMojo.OUT_PACKAGE;
        // use VelocityEngine javadoc
        mojo.proxyClass = VelocityEngine.class.getCanonicalName();
        mojo.project = new MavenProject((Model) null) {
            @Override
            public List getRuntimeClasspathElements() throws DependencyResolutionRequiredException {
                return Collections.EMPTY_LIST;
            }
        };
        mojo.excludePackages = JavadocApiMethodGeneratorMojo.DEFAULT_EXCLUDE_PACKAGES;

        mojo.execute();

        // check target file was generated
        assertExists(outFile);
    }
}
