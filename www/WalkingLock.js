var exec = require('cordova/exec');

var WalkingLock = {
    startTracking: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'WalkingLock', 'startTracking', []);
    },
    
    stopTracking: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'WalkingLock', 'stopTracking', []);
    },
    
    checkOverlayPermission: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'WalkingLock', 'checkOverlayPermission', []);
    },
    
    requestOverlayPermission: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'WalkingLock', 'requestOverlayPermission', []);
    }
};

module.exports = WalkingLock;