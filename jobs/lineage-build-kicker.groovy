import groovy.json.JsonSlurper

String getDevices() { ['curl', '-s', 'https://raw.githubusercontent.com/Stricted/jenkins/Stricted/lineage-targets.json'].execute().text }

def jsonParse(def json) { new groovy.json.JsonSlurperClassic().parseText(json) }

def global_repopick_nums = null
def global_repopick_tops = null

node("master") {
	timestamps {
		stage('Start Builds') {
			def json = jsonParse(getDevices())
			for(int i = 0; i < json.size(); i++) {
				if(json[i].device == "GLOBAL") {
					global_repopick_nums = json[i].repopick_nums
					global_repopick_tops = json[i].repopick_tops
					continue
				}
				echo "Kicking off a build for ${json[i].device}"
				build job: 'lineage-build', parameters: [
					string(name: 'VERSION', value: (json[i].version == null) ? "15.1" : json[i].version),
					string(name: 'DEVICE', value: (json[i].device == null) ? "HELP-omgwtfbbq" : json[i].device),
					string(name: 'BUILD_TYPE', value: (json[i].build_type == null) ? "userdebug" : json[i].build_type),
					string(name: 'REPOPICK_NUMBERS', value: (json[i].repopick_nums == null) ? "" : json[i].repopick_nums),
					string(name: 'REPOPICK_TOPICS', value: (json[i].repopick_tops == null) ? "" : json[i].repopick_tops),
					string(name: 'GLOBAL_REPOPICK_NUMBERS', value: (global_repopick_nums == null) ? "" : global_repopick_nums),
					string(name: 'GLOBAL_REPOPICK_TOPICS', value: (global_repopick_tops == null) ? "" : global_repopick_tops),
					string(name: 'WITH_SU', value: (json[i].with_su == null) ? "false" : json[i].with_su),
					string(name: 'WITH_DEXPREOPT', value: (json[i].with_dexpreopt == null) ? "false" : json[i].with_dexpreopt),
					string(name: 'OTA', value: (json[i].ota == null) ? "true" : json[i].ota),
					string(name: 'SIGNED', value: (json[i].signed == null) ? "false" : json[i].signed),
					string(name: 'SIGNED_BACKUPTOOL', value: (json[i].signed_backuptool == null) ? "true" : json[i].signed_backuptool),
					string(name: 'CLEAN', value: "true")
				], propagate: false, wait: false
				sleep 2
			}
		}
	}
}
