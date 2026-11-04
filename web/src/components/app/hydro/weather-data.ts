export interface CurrentWeather {
    city: string;
    region: string;
    country: string;
    latitude: number;
    longitude: number;
    temperature: number;
    feelsLike: number;
    humidity: number;
    precipitation: number;
    windSpeed: number;
    windGust: number;
    pressure: number;
    condition: string;
    icon: string;
    updatedAt: string;
}

export interface ForecastDay {
    date: string;
    day: {
        maxtemp_c: number;
        mintemp_c: number;
        avgtemp_c: number;
        totalprecip_mm: number;
        daily_chance_of_rain: number;
        condition: { text: string; icon: string };
    };
    hour: ForecastHour[];
}

export interface ForecastHour {
    time: string;
    temp_c: number;
    precip_mm: number;
    chance_of_rain: number;
    condition: { text: string; icon: string; code: number };
}

export interface WeatherForecast {
    forecastday: ForecastDay[];
}

export interface WeatherTimeZone {
    city: string;
    region: string;
    country: string;
    latitude: number;
    longitude: number;
    timeZone: string;
    localTimeEpoch: number;
    localTime: string;
}


