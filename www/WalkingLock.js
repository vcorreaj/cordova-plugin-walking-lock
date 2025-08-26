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
    },
    
    checkPermissions: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'WalkingLock', 'checkPermissions', []);
    },
    
    isTracking: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'WalkingLock', 'isTracking', []);
    },
    getStepCount: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'WalkingLock', 'getStepCount', []);
    },
    
    resetStepCount: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'WalkingLock', 'resetStepCount', []);
    },
    
    // Nuevo método para recibir updates en tiempo real
    startStepUpdates: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'WalkingLock', 'startStepUpdates', []);
    },
    
    stopStepUpdates: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'WalkingLock', 'stopStepUpdates', []);
    }
};

module.exports = WalkingLock;