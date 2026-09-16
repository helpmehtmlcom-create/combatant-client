#ifndef COMBATANT_CLOUD_WEATHER_BINDING
#error COMBATANT_CLOUD_WEATHER_BINDING must be defined before importing deferred_cloud_density.glsl
#endif
#ifndef COMBATANT_CLOUD_LAYER_BINDING
#error COMBATANT_CLOUD_LAYER_BINDING must be defined before importing deferred_cloud_density.glsl
#endif

const int COMBATANT_CLOUD_MAX_LAYERS = 8;

struct WeatherCell {
    vec4 climate;
    vec4 windFront;
};
layout(std430, binding = COMBATANT_CLOUD_WEATHER_BINDING) readonly buffer CloudWeatherData {
    WeatherCell cells[];
} u_Weather;

struct CloudLayer {
    vec4 altitudeDensity;
    vec4 scaleShape;
    vec4 weatherOptics;
    vec4 coverageShape;
};
layout(std430, binding = COMBATANT_CLOUD_LAYER_BINDING) readonly buffer CloudLayerData {
    CloudLayer layers[];
} u_Layers;

float combatant_cloud_hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33 + u_Data.noiseDomain.z * 17.0);
    return fract((p.x + p.y) * p.z);
}

float combatant_cloud_value_noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = combatant_cloud_hash31(i + vec3(0, 0, 0));
    float n100 = combatant_cloud_hash31(i + vec3(1, 0, 0));
    float n010 = combatant_cloud_hash31(i + vec3(0, 1, 0));
    float n110 = combatant_cloud_hash31(i + vec3(1, 1, 0));
    float n001 = combatant_cloud_hash31(i + vec3(0, 0, 1));
    float n101 = combatant_cloud_hash31(i + vec3(1, 0, 1));
    float n011 = combatant_cloud_hash31(i + vec3(0, 1, 1));
    float n111 = combatant_cloud_hash31(i + vec3(1, 1, 1));
    float x00 = mix(n000, n100, f.x);
    float x10 = mix(n010, n110, f.x);
    float x01 = mix(n001, n101, f.x);
    float x11 = mix(n011, n111, f.x);
    return mix(mix(x00, x10, f.y), mix(x01, x11, f.y), f.z);
}

float combatant_cloud_fbm(vec3 p) {
    float sum = 0.0;
    float weight = 0.5;
    for (int i = 0; i < 4; ++i) {
        sum += combatant_cloud_value_noise(p) * weight;
        p = p * 2.031 + vec3(17.0, 31.0, 47.0);
        weight *= 0.5;
    }
    return sum / 0.9375;
}

WeatherCell combatant_cloud_weather_cell(int x, int z) {
    int width = max(1, int(u_Data.grid.y));
    int depth = max(1, int(u_Data.grid.z));
    x = clamp(x, 0, width - 1);
    z = clamp(z, 0, depth - 1);
    int index = z * width + x;
    int sampleCount = max(0, int(u_Data.grid.w));
    if (index < 0 || index >= sampleCount) {
        WeatherCell empty;
        empty.climate = vec4(0.0);
        empty.windFront = vec4(0.0);
        return empty;
    }
    WeatherCell cell = u_Weather.cells[index];
    if (cell.windFront.w <= 0.5) {
        WeatherCell empty;
        empty.climate = vec4(0.0);
        empty.windFront = vec4(0.0);
        return empty;
    }
    return cell;
}

WeatherCell combatant_cloud_sample_weather(vec2 localXZ) {
    float spacing = max(u_Data.grid.x, 1.0);
    vec2 p = localXZ / spacing;
    ivec2 i = ivec2(floor(p));
    vec2 f = fract(p);
    WeatherCell a = combatant_cloud_weather_cell(i.x, i.y);
    WeatherCell b = combatant_cloud_weather_cell(i.x + 1, i.y);
    WeatherCell c = combatant_cloud_weather_cell(i.x, i.y + 1);
    WeatherCell d = combatant_cloud_weather_cell(i.x + 1, i.y + 1);
    WeatherCell result;
    result.climate = mix(mix(a.climate, b.climate, f.x), mix(c.climate, d.climate, f.x), f.y);
    result.windFront = mix(mix(a.windFront, b.windFront, f.x), mix(c.windFront, d.windFront, f.x), f.y);
    return result;
}

float combatant_cloud_layer_density(CloudLayer layer, vec3 localPosition, WeatherCell weather) {
    float baseY = layer.altitudeDensity.x;
    float topY = layer.altitudeDensity.y;
    float height01 = clamp((localPosition.y - baseY) / max(topY - baseY, 1.0), 0.0, 1.0);
    float vertical = smoothstep(0.0, layer.coverageShape.x, height01)
            * (1.0 - smoothstep(layer.coverageShape.y, 1.0, height01));
    if (vertical <= 0.0) return 0.0;

    vec2 advectedXZ = localPosition.xz + u_Data.noiseDomain.xy
            - weather.windFront.xy * u_Data.cameraTime.w;
    float macroScale = max(layer.scaleShape.x, 1.0);
    float detailScale = max(layer.scaleShape.y, 1.0);
    float macro = combatant_cloud_fbm(vec3(advectedXZ.x / macroScale, localPosition.y / macroScale,
            advectedXZ.y / macroScale));
    float detail = combatant_cloud_fbm(vec3(advectedXZ.x / detailScale, localPosition.y / detailScale,
            advectedXZ.y / detailScale));

    vec3 responses = max(layer.weatherOptics.xyz, vec3(0.0));
    float responseSum = max(responses.x + responses.y + responses.z, 1e-4);
    float weatherDrive = dot(vec3(weather.climate.x, weather.climate.z, weather.windFront.z), responses) / responseSum;
    float coverage = clamp(weatherDrive + layer.altitudeDensity.w, 0.0, 1.0);
    float threshold = mix(layer.coverageShape.z, layer.coverageShape.w, coverage);
    float shape = smoothstep(threshold - 0.10, threshold + 0.10, macro);
    shape *= mix(1.0, smoothstep(layer.scaleShape.z, 1.0, detail), layer.scaleShape.z);
    return max(0.0, shape * vertical * layer.altitudeDensity.z);
}

float combatant_cloud_density_at(vec3 localPosition, out float extinction, out float anisotropy) {
    WeatherCell weather = combatant_cloud_sample_weather(localPosition.xz);
    float density = 0.0;
    float weightedExtinction = 0.0;
    float weightedAnisotropy = 0.0;
    int layerCount = clamp(int(u_Data.counts.x), 0, COMBATANT_CLOUD_MAX_LAYERS);
    for (int i = 0; i < COMBATANT_CLOUD_MAX_LAYERS; ++i) {
        if (i >= layerCount) break;
        CloudLayer layer = u_Layers.layers[i];
        float d = combatant_cloud_layer_density(layer, localPosition, weather);
        density += d;
        weightedExtinction += d * layer.weatherOptics.w;
        weightedAnisotropy += d * layer.scaleShape.w;
    }
    if (density > 1e-6) {
        extinction = weightedExtinction / density;
        anisotropy = weightedAnisotropy / density;
    } else {
        extinction = 0.0;
        anisotropy = 0.0;
    }
    return density;
}

float combatant_cloud_optical_density_at(vec3 localPosition) {
    WeatherCell weather = combatant_cloud_sample_weather(localPosition.xz);
    float opticalDensity = 0.0;
    int layerCount = clamp(int(u_Data.counts.x), 0, COMBATANT_CLOUD_MAX_LAYERS);
    for (int i = 0; i < COMBATANT_CLOUD_MAX_LAYERS; ++i) {
        if (i >= layerCount) break;
        CloudLayer layer = u_Layers.layers[i];
        float density = combatant_cloud_layer_density(layer, localPosition, weather);
        opticalDensity += density * max(layer.weatherOptics.w, 0.0);
    }
    return opticalDensity;
}
