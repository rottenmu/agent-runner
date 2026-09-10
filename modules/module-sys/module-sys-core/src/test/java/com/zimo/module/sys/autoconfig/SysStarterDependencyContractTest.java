package com.zimo.module.sys.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class SysStarterDependencyContractTest {

    @Test
    void sysStarterBringsAuthStarterInfrastructure() throws Exception {
        Document pom = readPom(repositoryRoot().resolve("modules/module-sys/module-sys-core/pom.xml"));

        assertThat(hasDependency(pom, "com.zimo", "module-auth-core")).isTrue();
    }

    private boolean hasDependency(Document pom, String groupId, String artifactId) {
        var dependencies = pom.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            String actualGroupId = text(dependency, "groupId");
            String actualArtifactId = text(dependency, "artifactId");
            if (groupId.equals(actualGroupId) && artifactId.equals(actualArtifactId)) {
                return true;
            }
        }
        return false;
    }

    private String text(Element element, String tagName) {
        var nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private Document readPom(Path path) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
    }

    private Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null
                && !(Files.exists(current.resolve("pom.xml"))
                && Files.isDirectory(current.resolve("modules"))
                && Files.isDirectory(current.resolve("framework")))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }
}
