import type { ReactNode } from "react";
import { useEffect, useMemo, useState } from "react";
import { ArrowDown, ArrowUp, ChevronSelectorVertical, RefreshCw01, SearchMd } from "@untitledui/icons";
import { useTranslation } from "react-i18next";
import { Tabs } from "@/components/application/tabs/tabs";
import { FormAlert } from "@/components/app/form-alert";
import type { StateFeature } from "@/components/app/brazil-map/geo-data";
import { normalizeText } from "@/components/app/brazil-map/geo-data";
import { Button } from "@/components/base/buttons/button";
import { Input } from "@/components/base/input/input";
import { Select } from "@/components/base/select/select";
import { cx } from "@/utils/cx";
import type { HydroPoint, ThemeId } from "./hydro-data";
import { SENTINELA_TIME_ZONE } from "@/utils/time-zone";
import { THEME_CLASSES, titleCase } from "./hydro-data";

const PAGE_SIZE = 50;

interface Column<T> {
    id: string;
    header: string;
    /** Valor usado na ordenacao. */
    sortValue: (row: T) => string | number | null;
    render: (row: T) => ReactNode;
    align?: "right";
    /** Esconde a coluna em telas menores. */
    hideBelow?: "sm" | "md" | "lg" | "xl";
}

const HIDE = { sm: "hidden sm:table-cell", md: "hidden md:table-cell", lg: "hidden lg:table-cell", xl: "hidden xl:table-cell" };

/** Celula de cor + rotulo da classe (mesma cor do mapa e da legenda). */
const ClassCell = ({ color, label }: { color: string; label: string }) => (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
        <span className="size-2 shrink-0 rounded-full ring-1 ring-black/10" style={{ background: color }} />
        {label}
    </span>
);

const useRelativeTime = () => {
    const { i18n } = useTranslation();
    return useMemo(() => {
        const rtf = new Intl.RelativeTimeFormat(i18n.language, { numeric: "auto", style: "short" });
        return (hours: number | null | undefined) => {
            // Data no futuro e erro da fonte: nao mostra "em 200 dias".
            if (hours == null || hours < -1) return "—";
            if (hours < 1) return rtf.format(-Math.max(1, Math.round(hours * 60)), "minute");
            if (hours < 48) return rtf.format(-Math.round(hours), "hour");
            const days = hours / 24;
            if (days < 60) return rtf.format(-Math.round(days), "day");
            // Estacoes paradas ha anos: "ha 13 anos" em vez de "ha 4.833 dias".
            if (days < 730) return rtf.format(-Math.round(days / 30.44), "month");
            return rtf.format(-Math.round(days / 365.25), "year");
        };
    }, [i18n.language]);
};

// ---------- Tabela generica (ordenacao + paginacao) ----------

function DataTable<T>({
    rows,
    columns,
    rowKey,
    onRowClick,
    defaultSort,
    loading,
    empty,
}: {
    rows: T[];
    columns: Column<T>[];
    rowKey: (row: T) => string | number;
    onRowClick?: (row: T) => void;
    defaultSort: { id: string; dir: "asc" | "desc" };
    loading?: boolean;
    empty: ReactNode;
}) {
    const { t, i18n } = useTranslation();
    const [sort, setSort] = useState(defaultSort);
    const [page, setPage] = useState(0);
    const fmt = new Intl.NumberFormat(i18n.language);

    // Volta para a primeira pagina sempre que a lista ou a ordenacao mudam.
    useEffect(() => setPage(0), [rows, sort]);
    useEffect(() => setSort(defaultSort), [defaultSort.id, defaultSort.dir]);

    const sorted = useMemo(() => {
        const column = columns.find((c) => c.id === sort.id);
        if (!column) return rows;
        const collator = new Intl.Collator(i18n.language, { sensitivity: "base", numeric: true });
        const factor = sort.dir === "asc" ? 1 : -1;
        return [...rows].sort((a, b) => {
            const va = column.sortValue(a);
            const vb = column.sortValue(b);
            if (va == null && vb == null) return 0;
            if (va == null) return 1;
            if (vb == null) return -1;
            return (typeof va === "number" && typeof vb === "number" ? va - vb : collator.compare(String(va), String(vb))) * factor;
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [rows, sort, i18n.language]);

    const pages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
    const visible = sorted.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);

    const toggleSort = (id: string) =>
        setSort((s) => (s.id === id ? { id, dir: s.dir === "asc" ? "desc" : "asc" } : { id, dir: columns.find((c) => c.id === id)?.align === "right" ? "desc" : "asc" }));

    return (
        <div>
            <div className="max-h-[480px] overflow-auto">
                <table className="w-full border-separate border-spacing-0 text-sm">
                    <thead>
                        <tr>
                            {columns.map((c) => {
                                const active = sort.id === c.id;
                                const SortIcon = !active ? ChevronSelectorVertical : sort.dir === "asc" ? ArrowUp : ArrowDown;
                                return (
                                    <th
                                        key={c.id}
                                        scope="col"
                                        aria-sort={active ? (sort.dir === "asc" ? "ascending" : "descending") : "none"}
                                        className={cx(
                                            "sticky top-0 z-10 border-b border-secondary bg-secondary px-4 py-2 font-medium whitespace-nowrap text-tertiary first:pl-5 last:pr-5",
                                            c.align === "right" ? "text-right" : "text-left",
                                            c.hideBelow && HIDE[c.hideBelow],
                                        )}
                                    >
                                        <button
                                            type="button"
                                            onClick={() => toggleSort(c.id)}
                                            title={t("hydro.table.sort", { column: c.header })}
                                            className={cx(
                                                "inline-flex cursor-pointer items-center gap-1 text-xs transition-colors duration-100 hover:text-secondary",
                                                c.align === "right" && "flex-row-reverse",
                                                active && "text-secondary",
                                            )}
                                        >
                                            {c.header}
                                            <SortIcon className={cx("size-3.5", active ? "text-fg-secondary" : "text-fg-quaternary/60")} aria-hidden="true" />
                                        </button>
                                    </th>
                                );
                            })}
                        </tr>
                    </thead>
                    <tbody>
                        {loading
                            ? Array.from({ length: 8 }, (_, i) => (
                                  <tr key={i}>
                                      {columns.map((c) => (
                                          <td key={c.id} className={cx("border-b border-secondary px-4 py-3 first:pl-5 last:pr-5", c.hideBelow && HIDE[c.hideBelow])}>
                                              <span className="block h-3.5 w-full max-w-28 animate-pulse rounded bg-secondary" />
                                          </td>
                                      ))}
                                  </tr>
                              ))
                            : visible.map((row) => (
                                  <tr
                                      key={rowKey(row)}
                                      tabIndex={onRowClick ? 0 : undefined}
                                      onClick={() => onRowClick?.(row)}
                                      onKeyDown={(e) => {
                                          if (onRowClick && (e.key === "Enter" || e.key === " ")) {
                                              e.preventDefault();
                                              onRowClick(row);
                                          }
                                      }}
                                      className={cx(
                                          "group transition-colors duration-100",
                                          onRowClick && "cursor-pointer outline-none hover:bg-primary_hover focus-visible:bg-primary_hover",
                                      )}
                                  >
                                      {columns.map((c, i) => (
                                          <td
                                              key={c.id}
                                              className={cx(
                                                  "border-b border-secondary px-4 py-2.5 first:pl-5 last:pr-5",
                                                  i === 0 ? "text-primary" : "text-secondary",
                                                  c.align === "right" && "text-right tabular-nums",
                                                  c.hideBelow && HIDE[c.hideBelow],
                                              )}
                                          >
                                              {c.render(row)}
                                          </td>
                                      ))}
                                  </tr>
                              ))}
                    </tbody>
                </table>
                {!loading && sorted.length === 0 && <div className="px-5 py-10 text-center text-sm text-tertiary">{empty}</div>}
            </div>

            {!loading && sorted.length > 0 && (
                <div className="flex flex-wrap items-center justify-between gap-3 border-t border-secondary px-5 py-3">
                    <p className="text-xs text-tertiary tabular-nums">
                        {t("hydro.table.range", {
                            from: fmt.format(page * PAGE_SIZE + 1),
                            to: fmt.format(Math.min(sorted.length, (page + 1) * PAGE_SIZE)),
                            total: fmt.format(sorted.length),
                        })}
                    </p>
                    <div className="flex gap-2">
                        <Button size="xs" color="secondary" isDisabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                            {t("hydro.table.prev")}
                        </Button>
                        <Button size="xs" color="secondary" isDisabled={page >= pages - 1} onClick={() => setPage((p) => p + 1)}>
                            {t("hydro.table.next")}
                        </Button>
                    </div>
                </div>
            )}
        </div>
    );
}

// ---------- Painel da tabela (abas: tema atual | estados) ----------

interface HydroTableProps {
    theme: ThemeId;
    points: HydroPoint[];
    loading: boolean;
    error: string | null;
    onRetry: () => void;
    states: StateFeature[];
    /** Sigla do estado aberto no mapa (filtra a tabela). */
    scopeUf: string | null;
    onPointClick: (point: HydroPoint) => void;
    onStateClick: (uf: string) => void;
}

export const HydroTable = ({ theme, points, loading, error, onRetry, states, scopeUf, onPointClick, onStateClick }: HydroTableProps) => {
    const { t, i18n } = useTranslation();
    const ago = useRelativeTime();
    const num = useMemo(() => new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 }), [i18n.language]);
    const dateTime = useMemo(() => new Intl.DateTimeFormat(i18n.language, { dateStyle: "short", timeStyle: "short", timeZone: SENTINELA_TIME_ZONE }), [i18n.language]);
    const [tab, setTab] = useState<"data" | "states">("data");
    const [query, setQuery] = useState("");
    const [uf, setUf] = useState<string>("all");

    // O estado aberto no mapa vira o filtro da tabela.
    useEffect(() => setUf(scopeUf ?? "all"), [scopeUf]);

    const ufByName = useMemo(() => new Map(states.map((s) => [normalizeText(s.properties.nome), s.properties.sigla])), [states]);
    const siglaOf = (estado: string) => ufByName.get(normalizeText(estado)) ?? estado;
    const colorOf = useMemo(() => Object.fromEntries(THEME_CLASSES[theme].map((c) => [c.key, c])), [theme]);

    const filtered = useMemo(() => {
        const q = normalizeText(query);
        return points.filter((p) => (uf === "all" || siglaOf(p.estado) === uf) && (!q || p.search.includes(q)));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [points, query, uf, ufByName]);

    const stationCell = (p: HydroPoint) => (
        <span className="block min-w-40">
            <span className="block font-medium">{titleCase(p.nome)}</span>
            <span className="block text-xs text-quaternary tabular-nums">
                {p.codigo}
                {p.level?.situacaoEstacao === "MANUTENCAO" && <span className="font-medium text-warning-primary"> · {t("hydro.stations.maintenance")}</span>}
            </span>
        </span>
    );
    // Tempo relativo na celula; a data exata aparece ao passar o mouse.
    const updatedCell = (hours: number | null | undefined, iso: string | null | undefined) => (
        <span className="whitespace-nowrap" title={iso ? dateTime.format(new Date(iso)) : undefined}>
            {ago(hours)}
        </span>
    );
    const classCell = (p: HydroPoint) => {
        const c = colorOf[p.classKey];
        return c ? <ClassCell color={c.color} label={t(c.label)} /> : "—";
    };

    const columns: Column<HydroPoint>[] = useMemo(() => {
        const base: Column<HydroPoint>[] = [
            { id: "station", header: t("hydro.table.cols.station"), sortValue: (p) => p.nome, render: stationCell },
            { id: "city", header: t("hydro.table.cols.city"), sortValue: (p) => p.municipio, render: (p) => titleCase(p.municipio) || "—", hideBelow: "md" },
            { id: "uf", header: t("hydro.table.cols.uf"), sortValue: (p) => siglaOf(p.estado), render: (p) => siglaOf(p.estado) || "—" },
        ];
        if (theme === "rain") {
            return [
                ...base,
                { id: "rain", header: t("hydro.table.cols.rain"), align: "right", sortValue: (p) => p.rain?.chuvaMm24h ?? null, render: (p) => `${num.format(p.rain?.chuvaMm24h ?? 0)} mm` },
                { id: "class", header: t("hydro.table.cols.class"), sortValue: (p) => p.sort, render: classCell, hideBelow: "sm" },
                { id: "updated", header: t("hydro.table.cols.updated"), sortValue: (p) => p.rain?.idadeHoras ?? null, render: (p) => updatedCell(p.rain?.idadeHoras, p.rain?.dataUltimoDado), hideBelow: "lg" },
            ];
        }
        if (theme === "level") {
            return [
                ...base,
                { id: "basin", header: t("hydro.table.cols.basin"), sortValue: (p) => p.level?.subBacia ?? null, render: (p) => <span className="line-clamp-1 max-w-56">{titleCase(p.level?.subBacia) || "—"}</span>, hideBelow: "xl" },
                { id: "project", header: t("hydro.table.cols.project"), sortValue: (p) => p.level?.projeto ?? null, render: (p) => <span className="whitespace-nowrap">{p.level?.projeto ?? "—"}</span>, hideBelow: "lg" },
                { id: "operator", header: t("hydro.table.cols.operator"), sortValue: (p) => p.level?.operadora ?? null, render: (p) => <span className="whitespace-nowrap">{p.level?.operadora ?? "—"}</span>, hideBelow: "xl" },
                { id: "level", header: t("hydro.table.cols.level"), align: "right", sortValue: (p) => p.level?.cotaCm ?? null, render: (p) => (p.level?.cotaCm != null ? `${num.format(p.level.cotaCm)} cm` : "—") },
                { id: "status", header: t("hydro.table.cols.status"), sortValue: (p) => p.sort, render: classCell, hideBelow: "sm" },
                { id: "updated", header: t("hydro.table.cols.updated"), sortValue: (p) => p.level?.idadeHoras ?? null, render: (p) => updatedCell(p.level?.idadeHoras, p.level?.dataUltimoDado), hideBelow: "lg" },
            ];
        }
        return [
            ...base,
            { id: "type", header: t("hydro.table.cols.type"), sortValue: (p) => p.classKey, render: classCell, hideBelow: "sm" },
            { id: "river", header: t("hydro.table.cols.river"), sortValue: (p) => p.station?.rio ?? null, render: (p) => titleCase(p.station?.rio) || "—", hideBelow: "lg" },
            { id: "responsible", header: t("hydro.table.cols.responsible"), sortValue: (p) => p.station?.responsavel ?? null, render: (p) => p.station?.responsavel ?? "—", hideBelow: "xl" },
            {
                id: "status",
                header: t("hydro.table.cols.status"),
                sortValue: (p) => (p.active ? 1 : 0),
                render: (p) => (
                    <span className={cx("inline-flex items-center gap-1.5 text-xs font-medium whitespace-nowrap", p.active ? "text-success-primary" : "text-quaternary")}>
                        <span className={cx("size-1.5 rounded-full", p.active ? "bg-fg-success-secondary" : "bg-fg-quaternary")} />
                        {p.active ? t("hydro.stations.operating") : t("hydro.stations.inactive")}
                        {p.station?.telemetrica && <span className="text-tertiary">· {t("hydro.stations.telemetry")}</span>}
                    </span>
                ),
            },
        ];
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [theme, t, num, ago, dateTime, colorOf, ufByName]);

    const defaultSort = useMemo(
        () => (theme === "rain" ? { id: "rain", dir: "desc" as const } : theme === "level" ? { id: "status", dir: "desc" as const } : { id: "station", dir: "asc" as const }),
        [theme],
    );

    const stateRows = useMemo(() => {
        const q = normalizeText(query);
        return states.filter((s) => !q || normalizeText(`${s.properties.nome} ${s.properties.sigla} ${s.properties.capital ?? ""}`).includes(q));
    }, [states, query]);

    const stateColumns: Column<StateFeature>[] = useMemo(
        () => [
            {
                id: "state",
                header: t("hydro.table.cols.state"),
                sortValue: (s) => s.properties.nome,
                render: (s) => (
                    <span className="font-medium">
                        {s.properties.nome} <span className="text-xs font-normal text-quaternary">{s.properties.sigla}</span>
                    </span>
                ),
            },
            { id: "region", header: t("hydro.table.cols.region"), sortValue: (s) => s.properties.regiao, render: (s) => t(`map.regions.${s.properties.regiao}`), hideBelow: "sm" },
            { id: "capital", header: t("hydro.table.cols.capital"), sortValue: (s) => s.properties.capital, render: (s) => s.properties.capital ?? "—", hideBelow: "md" },
            { id: "cities", header: t("hydro.table.cols.cities"), align: "right", sortValue: (s) => s.properties.municipios, render: (s) => num.format(s.properties.municipios) },
            { id: "population", header: t("hydro.table.cols.population"), align: "right", sortValue: (s) => s.properties.populacao, render: (s) => (s.properties.populacao != null ? num.format(s.properties.populacao) : "—") },
        ],
        [t, num],
    );

    const ufItems = useMemo(
        () => [{ id: "all", label: t("hydro.table.allStates") }, ...[...states].sort((a, b) => a.properties.nome.localeCompare(b.properties.nome)).map((s) => ({ id: s.properties.sigla, label: `${s.properties.sigla} · ${s.properties.nome}` }))],
        [states, t],
    );

    return (
        <div className="border-t border-secondary">
            {/* Abas + filtros */}
            <div className="flex flex-wrap items-center gap-x-4 gap-y-3 px-5 pt-3">
                <Tabs selectedKey={tab} onSelectionChange={(k) => setTab(k as typeof tab)} className="w-auto">
                    <Tabs.List type="underline" size="sm" aria-label={t("hydro.table.show")}>
                        <Tabs.Item id="data" label={t(`hydro.themes.${theme}`)} />
                        <Tabs.Item id="states" label={t("hydro.table.tabStates")} />
                    </Tabs.List>
                </Tabs>
                <div className="flex w-full flex-wrap items-center gap-2 pb-3 sm:ml-auto sm:w-auto sm:flex-nowrap sm:pb-0">
                    <Input
                        size="sm"
                        icon={SearchMd}
                        aria-label={t("hydro.table.search")}
                        placeholder={t("hydro.table.search")}
                        value={query}
                        onChange={setQuery}
                        className="w-full sm:w-64"
                    />
                    {tab === "data" && (
                        <Select
                            size="sm"
                            aria-label={t("hydro.table.cols.state")}
                            items={ufItems}
                            selectedKey={uf}
                            onSelectionChange={(k) => setUf(String(k ?? "all"))}
                            className="w-full sm:w-52"
                            popoverClassName="max-h-72"
                        >
                            {(item) => <Select.Item id={item.id} label={item.label} />}
                        </Select>
                    )}
                </div>
            </div>

            <div className="mt-3 border-t border-secondary">
                {tab === "states" ? (
                    <DataTable
                        rows={stateRows}
                        columns={stateColumns}
                        rowKey={(s) => s.properties.sigla}
                        onRowClick={(s) => onStateClick(s.properties.sigla)}
                        defaultSort={{ id: "state", dir: "asc" }}
                        empty={t("hydro.table.empty")}
                    />
                ) : error ? (
                    <div className="flex flex-col items-center gap-3 px-5 py-10 text-center">
                        <FormAlert>{error}</FormAlert>
                        <p className="max-w-md text-xs text-quaternary">{t("hydro.table.restartBackend")}</p>
                        <Button size="sm" color="secondary" iconLeading={RefreshCw01} onClick={onRetry}>
                            {t("hydro.table.retry")}
                        </Button>
                    </div>
                ) : (
                    <DataTable
                        rows={filtered}
                        columns={columns}
                        rowKey={(p) => `${theme}-${p.codigo}-${p.id}`}
                        onRowClick={onPointClick}
                        defaultSort={defaultSort}
                        loading={loading}
                        empty={points.length === 0 ? t("hydro.table.noData") : t("hydro.table.empty")}
                    />
                )}
            </div>
        </div>
    );
};
