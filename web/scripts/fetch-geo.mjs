// Baixa as malhas (TopoJSON) e os nomes oficiais do IBGE e grava em public/geo.
// Uso: npm run geo
// Saida:
//   public/geo/br.json        estados (propriedades: id, sigla, nome, regiao, capital, municipios, populacao)
//   public/geo/uf/<UF>.json   municipios do estado (propriedades: id, nome, meso, micro, capital, populacao)
//   public/geo/busca.json     indice de busca [id, nome, uf] de todos os municipios
import { geoCentroid } from "d3-geo";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { feature } from "topojson-client";

const IBGE = "https://servicodados.ibge.gov.br";
const OUT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../public/geo");

// Codigo IBGE da capital de cada UF.
const CAPITAIS = {
    AC: 1200401, AL: 2704302, AP: 1600303, AM: 1302603, BA: 2927408, CE: 2304400, DF: 5300108,
    ES: 3205309, GO: 5208707, MA: 2111300, MT: 5103403, MS: 5002704, MG: 3106200, PA: 1501402,
    PB: 2507507, PR: 4106902, PE: 2611606, PI: 2211001, RJ: 3304557, RN: 2408102, RS: 4314902,
    RO: 1100205, RR: 1400100, SC: 4205407, SP: 3550308, SE: 2800308, TO: 1721000,
};

const getJson = async (url, tentativas = 4) => {
    for (let i = 1; i <= tentativas; i++) {
        try {
            const res = await fetch(IBGE + url);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            return await res.json();
        } catch (err) {
            if (i === tentativas) throw new Error(`${url}: ${err.message}`);
            await new Promise((r) => setTimeout(r, 1000 * i));
        }
    }
};

// Estimativa de populacao mais recente (agregado 6579). Se falhar, o mapa segue sem populacao.
const buscarPopulacao = async (nivel) => {
    try {
        const data = await getJson(`/api/v3/agregados/6579/periodos/-1/variaveis/9324?localidades=${nivel}[all]`);
        const serie = data[0].resultados[0].series;
        const ano = Object.keys(serie[0].serie)[0];
        const mapa = new Map(serie.map((s) => [Number(s.localidade.id), Number(s.serie[ano])]));
        return { ano, mapa };
    } catch (err) {
        console.warn(`Populacao (${nivel}) indisponivel: ${err.message}`);
        return { ano: null, mapa: new Map() };
    }
};

const primeiroObjeto = (topo) => topo.objects[Object.keys(topo.objects)[0]];

await mkdir(path.join(OUT, "uf"), { recursive: true });

const estados = await getJson("/api/v1/localidades/estados");
const [popUf, popMun] = await Promise.all([buscarPopulacao("N3"), buscarPopulacao("N6")]);

const totalMunicipios = {};
const capitais = {};
const busca = [];
for (const uf of estados) {
    const sigla = uf.sigla;
    const [topo, municipios] = await Promise.all([
        getJson(`/api/v3/malhas/estados/${sigla}?formato=application/json&qualidade=minima&intrarregiao=municipio`),
        getJson(`/api/v1/localidades/estados/${sigla}/municipios`),
    ]);

    const porId = new Map(municipios.map((m) => [m.id, m]));
    const geoms = primeiroObjeto(topo).geometries;
    let semNome = 0;

    for (const g of geoms) {
        const id = Number(g.properties.codarea);
        const m = porId.get(id);
        if (!m) semNome++;
        g.properties = {
            id,
            nome: m?.nome ?? String(id),
            meso: m?.microrregiao?.mesorregiao?.nome ?? null,
            micro: m?.microrregiao?.nome ?? null,
            capital: id === CAPITAIS[sigla],
            populacao: popMun.mapa.get(id) ?? null,
        };
    }

    if (!porId.has(CAPITAIS[sigla])) console.warn(`Capital de ${sigla} nao encontrada (${CAPITAIS[sigla]})`);
    if (semNome) console.warn(`${sigla}: ${semNome} municipio(s) sem nome`);

    const capital = feature(topo, primeiroObjeto(topo)).features.find((f) => f.properties.capital);
    if (capital) {
        const [lon, lat] = geoCentroid(capital);
        capitais[sigla] = { nome: capital.properties.nome, coord: [Number(lon.toFixed(4)), Number(lat.toFixed(4))] };
    }
    for (const g of geoms) busca.push([g.properties.id, g.properties.nome, sigla]);

    totalMunicipios[uf.id] = geoms.length;
    await writeFile(path.join(OUT, "uf", `${sigla}.json`), JSON.stringify(topo));
    console.log(`${sigla}: ${geoms.length} municipios`);
}

const br = await getJson("/api/v3/malhas/paises/BR?formato=application/json&qualidade=intermediaria&intrarregiao=UF");
const porUfId = new Map(estados.map((e) => [e.id, e]));

for (const g of primeiroObjeto(br).geometries) {
    const id = Number(g.properties.codarea);
    const uf = porUfId.get(id);
    const capitalId = CAPITAIS[uf.sigla];
    g.properties = {
        id,
        sigla: uf.sigla,
        nome: uf.nome,
        regiao: uf.regiao.sigla,
        capitalId,
        capital: capitais[uf.sigla]?.nome ?? null,
        capitalCoord: capitais[uf.sigla]?.coord ?? null,
        municipios: totalMunicipios[id],
        populacao: popUf.mapa.get(id) ?? null,
    };
}

br.meta = { fonte: "IBGE", anoPopulacao: popUf.ano ?? popMun.ano, geradoEm: new Date().toISOString().slice(0, 10) };
await writeFile(path.join(OUT, "br.json"), JSON.stringify(br));
await writeFile(path.join(OUT, "busca.json"), JSON.stringify(busca.sort((a, b) => a[1].localeCompare(b[1], "pt-BR"))));
console.log(`br.json: ${estados.length} estados, populacao ${br.meta.anoPopulacao ?? "indisponivel"}`);
