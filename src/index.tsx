import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-inapp-updates' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo managed workflow\n';

const InappUpdates = NativeModules.InappUpdates  ? NativeModules.InappUpdates  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

function startUpdateFlow(appUpdateType: string, clientVersionStalenessDays: number = 0): Promise<string> {
    let updateCode
    switch(appUpdateType){
      case 'immediate': {
        updateCode = 1;
        break;
      }
      case 'flexible': {
        updateCode = 0;
        break;
      };
      default: {
        updateCode = 1; 
        break;
      }
    }
    return InappUpdates.checkAppUpdate(updateCode,clientVersionStalenessDays);
}

function onCompleteUpdate(): Promise<string>{
  return InappUpdates.completeUpdate();

}

function checkUpdateAvailability(): Promise<string>{
  return InappUpdates.checkUpdateStatus();
}

export {
  startUpdateFlow ,
  onCompleteUpdate ,
  checkUpdateAvailability
}
