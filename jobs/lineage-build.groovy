String calcDate() { ['date', '+%Y%m%d'].execute().text.trim()}

def BUILD_TREE  = "/home/build/android/lineage/" + VERSION
def CCACHE_DIR  = "/home/build/.ccache"
def CERTS_DIR   = "/home/build/.android-certs"

def basejobname = DEVICE + '-' + VERSION + '-' + calcDate() + '-' + BUILD_TYPE

node("build") {
	timestamps {
		if(SIGNED == 'true') {
			basejobname = basejobname + '-signed'
		}
		if(OTA == 'true') {
			currentBuild.displayName = basejobname
		} else {
			currentBuild.displayName = basejobname + '-priv'
		}
		stage('Input manifest') {
			sh '''#!/bin/bash
				cd '''+BUILD_TREE+'''
				rm -rf .repo/local_manifests
				mkdir .repo/local_manifests
				curl --silent "https://raw.githubusercontent.com/Stricted/jenkins/Stricted/manifests/lineage-$VERSION/$DEVICE.xml" > .repo/local_manifests/roomservice.xml
			'''
		}
		stage('Sync') {
			sh '''#!/bin/bash
				set +x
				cd '''+BUILD_TREE+'''
				export GIT_SSH_COMMAND="ssh -o ControlPath=none"
				repo sync -d -c -j128 --force-sync
			'''
		}
		stage('Repopicks') {
			sh '''#!/bin/bash
				cd '''+BUILD_TREE+'''
				. build/envsetup.sh
				if ! [ -z $GLOBAL_REPOPICK_NUMBERS ]; then
					for rpnum in ${GLOBAL_REPOPICK_NUMBERS//,/ }; do
						repopick -fr $rpnum
					done
				else
					echo "No global repopick numbers chosen"
				fi
				if ! [ -z $GLOBAL_REPOPICK_TOPICS ]; then
					for rptopic in ${GLOBAL_REPOPICK_TOPICS//,/ }; do
						repopick -fr -t $rptopic
					done
				else
					echo "No global repopick topics chosen"
				fi
				if ! [ -z $REPOPICK_NUMBERS ]; then
					for rpnum in ${REPOPICK_NUMBERS//,/ }; do
						repopick -fr $rpnum
					done
				else
					echo "No repopick numbers chosen"
				fi
				if ! [ -z $REPOPICK_TOPICS ]; then
					for rptopic in ${REPOPICK_TOPICS//,/ }; do
						repopick -fr -t $rptopic
					done
				else
					echo "No repopick topics chosen"
				fi
			'''
		}
		stage('Clean') {
			if(CLEAN == 'true') {
				sh '''#!/bin/bash
					cd '''+BUILD_TREE+'''
					make clean
				'''
			}
			else {
				echo("skip cleaning");
			}
		}
		stage('Build') {
			sh '''#!/bin/bash +e
				cd '''+BUILD_TREE+'''
				. build/envsetup.sh
				export USE_CCACHE=1
				export CCACHE_COMPRESS=1
				export CCACHE_DIR='''+CCACHE_DIR+'''
				export ANDROID_COMPILE_WITH_JACK=false
				lunch lineage_$DEVICE-$BUILD_TYPE || lunch cm_$DEVICE-$BUILD_TYPE
				mka bacon
			'''
		}
		stage('Sign build') {
			if(SIGNED == 'true') {
				sh '''#!/bin/bash
				set -e
				cd '''+BUILD_TREE+'''
				OtaScriptPath=$([ -f out/target/product/$DEVICE/ota_script_path ] && cat "out/target/product/$DEVICE/ota_script_path" || echo "build/tools/releasetools/ota_from_target_files")
				rm -f out/target/product/$DEVICE/lineage-$VERSION-*.zip
				./build/tools/releasetools/sign_target_files_apks -o -d '''+CERTS_DIR+''' \
					out/target/product/$DEVICE/obj/PACKAGING/target_files_intermediates/*target_files*.zip \
					out/target/product/$DEVICE/jenkins-signed-target_files.zip
				$OtaScriptPath -k '''+CERTS_DIR+'''/releasekey \
					--block --backup=$SIGNED_BACKUPTOOL \
					out/target/product/$DEVICE/jenkins-signed-target_files.zip \
					out/target/product/$DEVICE/lineage-$VERSION-$(date +%Y%m%d)-UNOFFICIAL-$DEVICE-signed.zip
				'''
			}
			else {
				echo("skip signing");
			}
		}
		stage('Upload') {
			if(OTA == 'true') {
				sh '''#!/bin/bash
					zipname=$(find '''+BUILD_TREE+'''/out/target/product/$DEVICE/ -name 'lineage-'$VERSION'-*.zip' -type f -printf "%f\\n")
					ssh web52@stricted.net mkdir -p /var/www/web52/htdocs/lineageos/$DEVICE/
					scp '''+BUILD_TREE+'''/out/target/product/$DEVICE/$zipname web52@stricted.net:/var/www/web52/htdocs/lineageos/$DEVICE/
				'''
			}
			else {
				sh '''#!/bin/bash
					set -e
					cp '''+BUILD_TREE+'''/out/target/product/$DEVICE/lineage-$VERSION-* .
				'''
				archiveArtifacts artifacts: '*'
				sh '''#!/bin/bash
					rm *
				'''
			}
		}
	}
}
