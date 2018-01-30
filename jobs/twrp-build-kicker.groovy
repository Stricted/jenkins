import groovy.json.JsonSlurper

String getDevices() { ['curl', '-s', 'https://raw.githubusercontent.com/Stricted/jenkins/Stricted/resources/twrp-targets.json'].execute().text }

def jsonParse(def json) { new groovy.json.JsonSlurperClassic().parseText(json) }

node("master"){
  def json = jsonParse(getDevices())
  for(int i = 0; i < json.size(); i++) {
    if(device) {
      if(device != json[i].device) {
        if(version != json[i].version) {
          continue
        }
        continue
      }
    }
    echo "Kicking off a build for ${json[i].device}"
    build job: 'twrp-build', parameters: [
      string(name: 'VERSION', value: (json[i].version == null) ? "7.1" : json[i].version),
      string(name: 'DEVICE', value: (json[i].device == null) ? "HELP-omgwtfbbq" : json[i].device),
    ], propagate: false, wait: false
    sleep 2
  }
}
