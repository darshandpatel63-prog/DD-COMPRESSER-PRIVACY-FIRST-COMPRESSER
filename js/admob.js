// Native AdMob integration for the Android build.
// STORE-FIRST default: ads are OFF until a later monetized build injects config.
const TEST_BANNER = 'ca-app-pub-3940256099942544/9214589741';
const TEST_INTERSTITIAL = 'ca-app-pub-3940256099942544/1033173712';
const config = {
  enabled: false,
  testMode: false,
  appId: '',
  bannerId: '',
  interstitialId: '',
};
let initialized=false, consentReady=false, interstitialPrepared=false, completedCompressionCount=0;
function nativeAdMob(){ return window.Capacitor?.Plugins?.AdMob || null; }
function configured(){ return Boolean(config.appId && config.bannerId && config.interstitialId); }
function ids(){ return config.testMode ? {appId:config.appId || 'ca-app-pub-3940256099942544~3347511713',bannerId:TEST_BANNER,interstitialId:TEST_INTERSTITIAL} : config; }
export function isAdMobAvailable(){ return !!nativeAdMob() && config.enabled && (configured() || config.testMode); }
export async function initializeAds(){
  const AdMob=nativeAdMob();
  if(!AdMob || initialized || !isAdMobAvailable()) return false;
  try{
    const c=await AdMob.initialize();
    initialized=true;
    let consent=c;
    if(AdMob.requestConsentInfo){
      consent=await AdMob.requestConsentInfo();
      if(!consent?.canRequestAds && consent?.isConsentFormAvailable && AdMob.showConsentForm) consent=await AdMob.showConsentForm();
    }
    consentReady=consent?.canRequestAds !== false;
    if(consentReady) await prepareInterstitial();
    return consentReady;
  }catch(e){ console.warn('AdMob initialization failed:',e); return false; }
}
export async function showBanner(){
  const AdMob=nativeAdMob(); if(!AdMob||!consentReady||!isAdMobAvailable()) return false;
  try{
    await AdMob.showBanner({adId:ids().bannerId,adSize:'ADAPTIVE_BANNER',position:'BOTTOM_CENTER',margin:0});
    document.body.style.paddingBottom='72px'; return true;
  }catch(e){
    try{ await AdMob.showBanner({adId:ids().bannerId,adSize:'BANNER',position:'BOTTOM_CENTER',margin:0}); document.body.style.paddingBottom='60px'; return true; }
    catch(e2){ console.warn('AdMob banner failed:',e2); return false; }
  }
}
export async function prepareInterstitial(){
  const AdMob=nativeAdMob(); if(!AdMob||!consentReady||!isAdMobAvailable()) return false;
  try{ await AdMob.prepareInterstitial({adId:ids().interstitialId}); interstitialPrepared=true; return true; }
  catch(e){ interstitialPrepared=false; console.warn('AdMob interstitial prepare failed:',e); return false; }
}
export async function recordCompletedCompression(){
  if(!isAdMobAvailable()||!consentReady) return;
  completedCompressionCount++;
  if(completedCompressionCount<20) return;
  completedCompressionCount=0;
  const AdMob=nativeAdMob();
  try{
    if(!interstitialPrepared) await prepareInterstitial();
    if(interstitialPrepared){ await AdMob.showInterstitial(); interstitialPrepared=false; await prepareInterstitial(); }
  }catch(e){ console.warn('AdMob interstitial failed:',e); interstitialPrepared=false; await prepareInterstitial(); }
}
export async function showAdPrivacyOptions(){
  const AdMob=nativeAdMob(); if(!AdMob||!consentReady||!isAdMobAvailable()) return false;
  try{ await AdMob.showPrivacyOptionsForm(); return true; }catch(e){ console.warn('Ad privacy options unavailable:',e); return false; }
}
