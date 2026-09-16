#ifndef COMBATANT_DEFERRED_FROXEL_MEDIA_DATA_GLSL
#define COMBATANT_DEFERRED_FROXEL_MEDIA_DATA_GLSL

layout(std430, binding = 0) readonly buffer FroxelMediaData {
    mat4 inverseProjection;
    mat4 inverseView;
    vec4 cameraTime;
    vec4 grid;
    vec4 counts;
    vec4 noiseDomain;
    vec4 froxel;
    vec4 policy;
    vec4 shadowMapDomain;
    vec4 shadowSun;
    vec4 cloudBounds;
    vec4 aerialLayout;
    vec4 mediumDistribution;
    vec4 mediumScattering;
    vec4 mediumAbsorption;
    vec4 mediumWeather;
    vec4 mediumWeatherState;
    vec4 mediumLighting;
    vec4 directionalDirection;
    vec4 directionalRadiance;
} u_Data;

#endif
