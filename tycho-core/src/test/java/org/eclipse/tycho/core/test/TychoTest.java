/*******************************************************************************
 * Copyright (c) 2008, 2020 Sonatype Inc. and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.core.test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.tycho.ClasspathEntry;
import org.eclipse.tycho.TargetEnvironment;
import org.eclipse.tycho.core.TargetPlatformConfiguration;
import org.eclipse.tycho.core.TychoProject;
import org.eclipse.tycho.core.osgitools.DefaultReactorProject;
import org.eclipse.tycho.core.osgitools.OsgiBundleProject;
import org.eclipse.tycho.core.resolver.DefaultTargetPlatformConfigurationReader;
import org.eclipse.tycho.core.resolver.TargetPlatformConfigurationException;
import org.eclipse.tycho.p2.resolver.ResolverException;
import org.eclipse.tycho.testing.AbstractTychoMojoTestCase;
import org.eclipse.tycho.version.TychoVersion;

public class TychoTest extends AbstractTychoMojoTestCase {

    protected Logger logger;
    private Properties properties;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        logger = new SilentLog();
        properties = new Properties();
        properties.setProperty("tycho-version", TychoVersion.getTychoVersion());
    }

    @Override
    protected void tearDown() throws Exception {
        logger = null;
        super.tearDown();
    }

    public void testModuleOrder() throws Exception {
        File basedir = getBasedir("projects/moduleorder");

        List<MavenProject> projects = getSortedProjects(basedir);
        assertEquals(5, projects.size());

        MavenProject p002 = projects.get(1);
        MavenProject p001 = projects.get(2);
        MavenProject p004 = projects.get(3); // feature
        MavenProject p003 = projects.get(4); // site

        assertEquals("moduleorder.p001", p001.getArtifactId());
        assertEquals("moduleorder.p002", p002.getArtifactId());
        assertEquals("moduleorder.p003", p003.getArtifactId());
        assertEquals("moduleorder.p004", p004.getArtifactId());
    }

    public void testResolutionError() throws Exception {
        File basedir = getBasedir("projects/resolutionerror/p001");

        try {
            getSortedProjects(basedir);
            fail();
        } catch (Exception e) {
            assertResolveError(e,
                    "Missing requirement: moduleorder.p001 0.0.1 requires 'java.package; moduleorder.p002 0.0.0' but it could not be found");
        }
    }

    private void assertResolveError(Throwable e, String string) {
        if (e instanceof ResolverException re) {
            String details = re.getDetails();
            assertTrue(string + " not found in details: " + details, details.contains(string));
            return;
        }
        if (e.getCause() != null) {
            assertResolveError(e.getCause(), string);
            return;
        }
        fail("Resolve error was not found: " + string);
    }

    public void testFeatureMissingFeature() throws Exception {
        File basedir = getBasedir("projects/resolutionerror/feature_missing_feature");
        try {
            getSortedProjects(basedir);
            fail();
        } catch (Exception e) {
            assertResolveError(e,
                    "feature_missing_feature.feature.group 1.0.0 requires 'org.eclipse.equinox.p2.iu; feature.not.found.feature.group 0.0.0' but it could not be found");
        }
    }

    public void testFeatureMissingPlugin() throws Exception {
        File basedir = getBasedir("projects/resolutionerror/feature_missing_plugin");
        try {
            getSortedProjects(basedir);
            fail();
        } catch (Exception e) {
            assertResolveError(e,
                    "feature_missing_feature.feature.group 1.0.0 requires 'org.eclipse.equinox.p2.iu; plugin.not.found 0.0.0' but it could not be found");
        }
    }

    public void testProjectPriority() throws Exception {
        File basedir = getBasedir("projects/projectpriority");
        List<MavenProject> projects = getSortedProjects(basedir, properties);

        MavenProject p002 = projects.get(2);

        List<Dependency> dependencies = p002.getModel().getDependencies();
        Dependency dependency = dependencies.get(0);
        assertEquals("0.0.1", dependency.getVersion());
    }

    public void testMissingClasspathEntries() throws Exception {
        File basedir = getBasedir("projects/missingentry");
        MavenProject project = getSortedProjects(basedir, properties).get(0);

        OsgiBundleProject projectType = (OsgiBundleProject) lookup(TychoProject.class, project.getPackaging());

        List<ClasspathEntry> classpath = projectType.getClasspath(DefaultReactorProject.adapt(project));

        assertEquals(3, classpath.size());
        assertEquals(1, classpath.get(0).getLocations().size());
        assertEquals(canonicalFile("src/test/resources/targetplatforms/missingentry/plugins/dirbundle_0.0.1"),
                classpath.get(0).getLocations().get(0).getCanonicalFile());
        assertEquals(1, classpath.get(1).getLocations().size());
        assertEquals(canonicalFile("src/test/resources/targetplatforms/missingentry/plugins/jarbundle_0.0.1.jar"),
                classpath.get(1).getLocations().get(0).getCanonicalFile());
    }

    private File canonicalFile(String path) throws IOException {
        return new File(path).getCanonicalFile();
    }

    public void testBundleExtraClasspath() throws Exception {
        File basedir = getBasedir("projects/extraclasspath");
        List<MavenProject> projects = getSortedProjects(basedir, properties);
        assertEquals(3, projects.size());

        MavenProject b02 = projects.get(2);

        OsgiBundleProject projectType = (OsgiBundleProject) lookup(TychoProject.class, b02.getPackaging());

        List<ClasspathEntry> classpath = projectType.getClasspath(DefaultReactorProject.adapt(b02));

        assertClasspathContains(classpath, canonicalFile(
                "target/local-repo/.cache/tycho/org.eclipse.equinox.launcher_1.0.101.R34x_v20081125.jar/launcher.properties"));
        assertClasspathContains(classpath,
                canonicalFile("target/projects/extraclasspath/b01/target/lib/nested.jar-classes"));
        assertClasspathContains(classpath, canonicalFile(
                "src/test/resources/targetplatforms/basic/plugins/org.eclipse.equinox.launcher_1.0.101.R34x_v20081125.jar"));
        assertClasspathContains(classpath, new File(basedir, "b02/classes"));
        assertClasspathContains(classpath, canonicalFile("target/projects/extraclasspath/b02/target/classes"));
    }

    private void assertClasspathContains(List<ClasspathEntry> classpath, File file) throws IOException {
        for (ClasspathEntry cpe : classpath) {
            for (File cpfile : cpe.getLocations()) {
                if (cpfile.getCanonicalFile().equals(file.getCanonicalFile())) {
                    return;
                }
            }
        }

        fail("File " + file + " not found on the classpath");

    }

    public void testImplicitTargetEnvironment() throws Exception {
        File basedir = getBasedir("projects/implicitenvironment/simple");

        List<MavenProject> projects = getSortedProjects(basedir);
        assertEquals(1, projects.size());

//        assertEquals("ambiguous", projects.get(0).getArtifactId());
//        assertEquals("none", projects.get(0).getArtifactId());
        assertEquals("simple", projects.get(0).getArtifactId());

        DefaultTargetPlatformConfigurationReader resolver = lookup(DefaultTargetPlatformConfigurationReader.class);

        MavenSession session;
        TargetPlatformConfiguration configuration;
        List<TargetEnvironment> environments;

        // ambiguous
//        session = newMavenSession(projects.get(0), projects);
//        configuration = resolver.getTargetPlatformConfiguration(session, session.getCurrentProject());
//        environments = configuration.getEnvironments();
//        assertEquals(0, environments.size());

        // none
//        session = newMavenSession(projects.get(0), projects);
//        configuration = resolver.getTargetPlatformConfiguration(session, session.getCurrentProject());
//        environments = configuration.getEnvironments();
//        assertEquals(0, environments.size());

        // simple
        session = newMavenSession(projects.get(0), projects);
        configuration = resolver.getTargetPlatformConfiguration(session, session.getCurrentProject());
        environments = configuration.getEnvironments();
        assertEquals(1, environments.size());
        TargetEnvironment env = environments.get(0);
        assertEquals("foo", env.getOs());
        assertEquals("bar", env.getWs());
        assertEquals("munchy", env.getArch());
    }

    public void testWithValidExplicitTargetEnvironment() throws Exception {
        File basedir = getBasedir("projects/explicitenvironment/valid");

        List<MavenProject> projects = getSortedProjects(basedir);
        assertEquals(1, projects.size());

        assertEquals("valid", projects.get(0).getArtifactId());

        DefaultTargetPlatformConfigurationReader resolver = lookup(DefaultTargetPlatformConfigurationReader.class);

        MavenSession session = newMavenSession(projects.get(0), projects);

        TargetPlatformConfiguration configuration;
        List<TargetEnvironment> environments;

        configuration = resolver.getTargetPlatformConfiguration(session, session.getCurrentProject());
        environments = configuration.getEnvironments();
        assertEquals(1, environments.size());
        TargetEnvironment env = environments.get(0);
        assertEquals("linux", env.getOs());
        assertEquals("gtk", env.getWs());
        assertEquals("arm", env.getArch());
    }

    public void testWithMissingOsInExplicitTargetEnvironment() throws Exception {
        File basedir = getBasedir("projects/explicitenvironment/missingOs");
        try {
            getSortedProjects(basedir);
            fail("RuntimeException must be thrown when <os> is missing in the target configuration (environment element)");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains(
                    "target-platform-configuration error in project explicitenvironment:missingos:eclipse-plugin"));
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertTrue(cause instanceof TargetPlatformConfigurationException);
            assertEquals("<os> element is missing within target-platform-configuration (element <environment>)",
                    cause.getMessage());
        }
    }

    public void testWithMissingWsInExplicitTargetEnvironment() throws Exception {
        File basedir = getBasedir("projects/explicitenvironment/missingWs");
        try {
            getSortedProjects(basedir);
            fail("RuntimeException must be thrown when <ws> is missing in the target configuration (environment element)");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains(
                    "target-platform-configuration error in project explicitenvironment:missingws:eclipse-plugin"));
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertTrue(cause instanceof TargetPlatformConfigurationException);
            assertEquals("<ws> element is missing within target-platform-configuration (element <environment>)",
                    cause.getMessage());
        }
    }

    public void testWithMissingArchInExplicitTargetEnvironment() throws Exception {
        File basedir = getBasedir("projects/explicitenvironment/missingArch");
        try {
            getSortedProjects(basedir);
            fail("RuntimeException must be thrown when <arch> is missing in the target configuration (environment element)");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains(
                    "target-platform-configuration error in project explicitenvironment:missingarch:eclipse-plugin"));
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertTrue(cause instanceof TargetPlatformConfigurationException);
            assertEquals("<arch> element is missing within target-platform-configuration (element <environment>)",
                    cause.getMessage());
        }
    }

    public void testWithProjectReferencesItself() throws Exception {
        //Does not work anymore
//        File basedir = getBasedir("projects/referencesItself");
//        try {
//            getSortedProjects(basedir);
//            fail();
//        } catch (Exception e) {
//            assertTrue(e.getMessage().contains("Bundle referencesItself cannot be resolved"));
//        }

    }

}
