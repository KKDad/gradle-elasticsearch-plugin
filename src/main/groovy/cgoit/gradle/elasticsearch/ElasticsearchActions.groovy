package cgoit.gradle.elasticsearch

import de.undercouch.gradle.tasks.download.DownloadAction
import org.gradle.api.Project

import static ElasticsearchPlugin.CYAN
import static ElasticsearchPlugin.NORMAL
import static org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS
import static org.apache.tools.ant.taskdefs.condition.Os.isFamily

class ElasticsearchActions {
    String version
    File toolsDir
    Project project
    AntBuilder ant
    File home

    ElasticsearchActions(Project project, File toolsDir, String version) {
        this.project = project
        this.toolsDir = toolsDir
        this.version = version
        this.ant = project.ant
        home = new File("$toolsDir/elastic")
    }

    boolean isInstalled() {
      if (!new File("$home/bin/elasticsearch").exists()) {
        return false
      }

        boolean desiredVersion = isDesiredVersion()

        if (!desiredVersion) {
            // this is not the desired version, then we also need to delete the home directory
            ant.delete(dir: home)

            return false
        }

        return true
    }

    boolean isDesiredVersion() {
        println "${CYAN}* elastic:$NORMAL checking existing version"

        def versionFile = new File("$home/version.txt")
      if (!versionFile.exists()) {
        return false
      }

        def detectedVersion = versionFile.text

        println "${CYAN}* elastic:$NORMAL: detected version: $detectedVersion"

        return detectedVersion.contains(version)
    }

    void install() {
        if (isInstalled()) {
            println "${CYAN}* elastic:$NORMAL elastic search version $version detected at $home"
            return
        }

        println "${CYAN}* elastic:$NORMAL installing elastic version $version"

        def majorVersion = Integer.valueOf(version.split("\\.")[0])

        String linuxUrl
        String winUrl

        switch (majorVersion) {
            case 0:
            case 1:
                linuxUrl = "https://download.elastic.co/elasticsearch/elasticsearch/elasticsearch-${version}.tar.gz"
                winUrl = "https://download.elastic.co/elasticsearch/elasticsearch/elasticsearch-${version}.zip"
                break

            case 2:
                linuxUrl = "https://download.elasticsearch.org/elasticsearch/release/org/elasticsearch/distribution/tar/elasticsearch/${version}/elasticsearch-${version}.tar.gz"
                winUrl = "https://download.elasticsearch.org/elasticsearch/release/org/elasticsearch/distribution/zip/elasticsearch/${version}/elasticsearch-${version}.zip"
                break

        // there are no versions 3 and 4

            case 7:
                linuxUrl = "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${version}-linux-x86_64.tar.gz"
                winUrl = "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${version}-windows-x86_64.zip"
                break


            default: // catches version 5 and up
                linuxUrl = "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${version}.tar.gz"
                winUrl = "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${version}.zip"
                break
        }

        String elasticPackage = isFamily(FAMILY_WINDOWS) ? winUrl : linuxUrl
        File elasticFile = new File("$toolsDir/elastic-${version}.${isFamily(FAMILY_WINDOWS) ? 'zip' : 'tar.gz'}")
        File elasticFilePart = new File("$toolsDir/elastic-${version}.${isFamily(FAMILY_WINDOWS) ? 'zip' : 'tar.gz'}.part")

        ant.delete(quiet: true) {
            fileset(dir: toolsDir) {
                include(name: "**/*.part")
            }
        }

        DownloadAction elasticDownload = new DownloadAction(project)
        elasticDownload.dest(elasticFilePart)
        elasticDownload.src(elasticPackage)
        elasticDownload.onlyIfNewer(true)
        elasticDownload.execute()

        ant.rename(src: elasticFilePart, dest: elasticFile, replace: true)

        ant.delete(dir: home, quiet: true)
        home.mkdirs()

        if (isFamily(FAMILY_WINDOWS)) {
            ant.unzip(src: elasticFile, dest: "$home") {
                cutdirsmapper(dirs: 1)
            }
        } else {
            ant.untar(src: elasticFile, dest: "$home", compression: "gzip") {
                cutdirsmapper(dirs: 1)
            }
            ant.chmod(file: new File("$home/bin/elasticsearch"), perm: "+x")
            ant.chmod(file: new File("$home/bin/plugin"), perm: "+x")
            ant.chmod(file: new File("$home/modules/x-pack-ml/platform/linux-x86_64/bin/controller"), perm: "+x")
        }

        new File("$home/version.txt") << "$version"
    }
}
