import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.viettel.caobang.dieuhanh',
  appName: 'Điều hành Viettel Cao Bằng',
  webDir: 'www',
  android: {
    allowMixedContent: false,
    webContentsDebuggingEnabled: false,
    backgroundColor: '#E80500'
  }
};

export default config;
