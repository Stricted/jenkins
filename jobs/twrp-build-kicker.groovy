import groovy.json.JsonSlurper

String getDevices() { ['curl', '-s', 'https://raw.githubusercontent.com/Stricted/jenkins/Stricted/twrp-targets.json'].execute().text }

def jsonParse(def json) { new groovy.json.JsonSlurperClassic().parseText(json) }

node("master"){
timestamps {
stage('Start Builds'){
  def json = jsonParse(getDevices())
  for(int i = 0; i < json.size(); i++) {
    echo "Kicking off a build for ${json[i].device}"
    build job: 'twrp-build', parameters: [
      string(name: 'VERSION', value: (json[i].version == null) ? "7.1" : json[i].version),
      string(name: 'DEVICE', value: (json[i].device == null) ? "HELP-omgwtfbbq" : json[i].device),
      string(name: 'MANIFEST', value: (json[i].manifest == null) ? "" : json[i].manifest),
      string(name: 'CLEAN', value: "true")
    ], propagate: false, wait: false
    sleep 2
  }
}
}
}
