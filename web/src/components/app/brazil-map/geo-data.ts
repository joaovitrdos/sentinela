import type { Feature, FeatureCollection, MultiLineString, MultiPolygon, Polygon } from "geojson";
import { feature, mesh } from "topojson-client";
import type { GeometryCollection, Topology } from "topojson-specification";

// Arquivos gerados por `npm run geo` (scripts/fetch-geo.mjs) a partir das malhas oficiais do IBGE.

export type RegionCode = "N" | "NE" | "CO" | "SE" | "S";

export interface StateProps {
    id: number;
    sigla: string;
    nome: string;
    regiao: RegionCode;
    capitalId: number;
    capital: string | null;
    capitalCoord: [number, number] | null;
    municipios: number;
    populacao: number | null;
}

export interface CityProps {
    id: number;
    nome: string;
    meso: string | null;
    micro: string | null;
    capital: boolean;
    populacao: number | null;
}

type Shape = Polygon | MultiPolygon;
export type StateFeature = Feature<Shape, StateProps>;
export type CityFeature = Feature<Shape, CityProps>;

export interface CountryData {
    states: StateFeature[];
    /** Divisas entre estados (sem repetir linhas). */
    borders: MultiLineString;
    /** Contorno do Brasil. */
    outline: MultiLineString;
    populationYear: string | null;
}

export interface StateData {
    cities: CityFeature[];
    borders: MultiLineString;
}

/** [codigo IBGE, nome, UF] */
export type SearchEntry = [number, string, string];

const fetchJson = async <T>(url: string): Promise<T> => {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`${url}: HTTP ${res.status}`);
    return res.json() as Promise<T>;
};

const firstObject = (topo: Topology) => topo.objects[Object.keys(topo.objects)[0]] as GeometryCollection;

// Cache em memoria: cada arquivo e baixado uma vez por sessao.
let countryPromise: Promise<CountryData> | null = null;
const statePromises = new Map<string, Promise<StateData>>();
let searchPromise: Promise<SearchEntry[]> | null = null;

const remember = <T>(promise: Promise<T>, forget: () => void) => {
    promise.catch(forget);
    return promise;
};

export const loadCountry = (): Promise<CountryData> => {
    countryPromise ??= remember(
        fetchJson<Topology & { meta?: { anoPopulacao?: string } }>("/geo/br.json").then((topo) => {
            const object = firstObject(topo);
            const collection = feature(topo, object) as FeatureCollection<Shape, StateProps>;
            return {
                states: collection.features,
                borders: mesh(topo, object, (a, b) => a !== b),
                outline: mesh(topo, object, (a, b) => a === b),
                populationYear: topo.meta?.anoPopulacao ?? null,
            };
        }),
        () => (countryPromise = null),
    );
    return countryPromise;
};

export const loadState = (uf: string): Promise<StateData> => {
    let promise = statePromises.get(uf);
    if (!promise) {
        promise = remember(
            fetchJson<Topology>(`/geo/uf/${uf}.json`).then((topo) => {
                const object = firstObject(topo);
                const collection = feature(topo, object) as FeatureCollection<Shape, CityProps>;
                return { cities: collection.features, borders: mesh(topo, object, (a, b) => a !== b) };
            }),
            () => statePromises.delete(uf),
        );
        statePromises.set(uf, promise);
    }
    return promise;
};

export const loadSearchIndex = (): Promise<SearchEntry[]> => {
    searchPromise ??= remember(fetchJson<SearchEntry[]>("/geo/busca.json"), () => (searchPromise = null));
    return searchPromise;
};

/** Remove acentos e caixa para comparar nomes ("sao paulo" acha "São Paulo"). */
export const normalizeText = (text: string) =>
    text
        .normalize("NFD")
        .replace(/[̀-ͯ]/g, "")
        .toLowerCase()
        .trim();
