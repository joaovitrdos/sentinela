import { memo, useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { TooltipContentProps } from "recharts";
import { Bar, BarChart, CartesianGrid, Cell, LabelList, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { SENTINELA_TIME_ZONE } from "@/utils/time-zone";

export interface LevelChartRow {
    id: string;
    name: string;
    city: string;
    value: number;
    statusLabel: string;
    color: string;
    updatedAt: string | null | undefined;
}

export interface HourlyChartRow {
    label: string;
    temperature: number;
    rain: number;
    condition?: string;
}

const AXIS_TICK = { fill: "var(--color-text-tertiary)", fontSize: 11 };
const WEATHER_COLORS = { temperature: "var(--color-utility-orange-500)", rain: "var(--color-utility-blue-500)" };

function ChartTooltip({ active, payload, label, kind }: TooltipContentProps & { kind: "weather" | "level" }) {
    const { t, i18n } = useTranslation();
    const number = useMemo(() => new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 }), [i18n.language]);
    const dateTime = useMemo(
        () => new Intl.DateTimeFormat(i18n.language, { dateStyle: "short", timeStyle: "short", timeZone: SENTINELA_TIME_ZONE }),
        [i18n.language],
    );
    if (!active || !payload.length) return null;
    const row = kind === "level" ? (payload[0].payload as LevelChartRow) : null;
    const updatedAt = row?.updatedAt ? new Date(row.updatedAt) : null;

    return (
        <div className="max-w-64 rounded-xl border border-secondary bg-primary p-3 text-xs text-secondary shadow-lg">
            <p className="font-semibold break-words text-primary">{row?.name ?? label}</p>
            {row?.city && <p className="mt-1 text-tertiary">{row.city}</p>}
            <dl className="mt-2 space-y-2">
                {payload.map((entry) => (
                    <div key={String(entry.dataKey)} className="flex items-center justify-between gap-4">
                        <dt className="flex items-center gap-2 text-secondary">
                            <span aria-hidden="true" className="size-2 shrink-0 rounded-full" style={{ backgroundColor: row?.color ?? entry.color }} />
                            {entry.name}
                        </dt>
                        <dd className="shrink-0 font-semibold text-primary tabular-nums">
                            {number.format(Number(entry.value))} {kind === "level" ? "cm" : entry.dataKey === "temperature" ? "°C" : "mm"}
                        </dd>
                    </div>
                ))}
            </dl>
            {row && <p className="mt-2 border-t border-secondary pt-2 text-secondary">{t(row.statusLabel)}</p>}
            {updatedAt && Number.isFinite(updatedAt.getTime()) && (
                <p className="mt-1 text-tertiary">{t("dashboard.updatedAt", { time: dateTime.format(updatedAt) })}</p>
            )}
        </div>
    );
}

export const HourlyWeatherChart = memo(function HourlyWeatherChart({ data }: { data: HourlyChartRow[] }) {
    const { t, i18n } = useTranslation();
    const number = useMemo(() => new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 }), [i18n.language]);
    return (
        <>
            <div className="h-80 min-w-0 sm:h-[396px]" role="group" aria-label={t("dashboard.charts.weatherTitle")}>
                <ResponsiveContainer width="100%" height="100%" minWidth={0} debounce={100}>
                    <LineChart data={data} margin={{ top: 12, right: 8, bottom: 8, left: 0 }}>
                        <CartesianGrid stroke="var(--color-border-secondary)" strokeDasharray="3 3" vertical={false} />
                        <XAxis dataKey="label" tick={AXIS_TICK} tickLine={false} axisLine={false} minTickGap={32} tickMargin={10} />
                        <YAxis
                            yAxisId="temperature"
                            tickFormatter={(value) => `${number.format(Number(value))}°`}
                            tick={AXIS_TICK}
                            tickLine={false}
                            axisLine={false}
                            width={48}
                        />
                        <YAxis
                            yAxisId="rain"
                            orientation="right"
                            tickFormatter={(value) => number.format(Number(value))}
                            tick={AXIS_TICK}
                            tickLine={false}
                            axisLine={false}
                            width={42}
                        />
                        <Tooltip
                            content={(props) => <ChartTooltip {...props} kind="weather" />}
                            cursor={{ stroke: "var(--color-border-primary)", strokeDasharray: "4 4" }}
                            isAnimationActive={false}
                        />
                        <Line
                            yAxisId="temperature"
                            type="monotone"
                            dataKey="temperature"
                            name={t("dashboard.charts.temperatureLine")}
                            stroke={WEATHER_COLORS.temperature}
                            strokeWidth={2.5}
                            dot={false}
                            activeDot={{ r: 4, stroke: "var(--color-bg-primary)", strokeWidth: 2 }}
                            isAnimationActive={false}
                        />
                        <Line
                            yAxisId="rain"
                            type="monotone"
                            dataKey="rain"
                            name={t("dashboard.charts.rainLine")}
                            stroke={WEATHER_COLORS.rain}
                            strokeWidth={2}
                            dot={false}
                            activeDot={{ r: 4, stroke: "var(--color-bg-primary)", strokeWidth: 2 }}
                            isAnimationActive={false}
                        />
                    </LineChart>
                </ResponsiveContainer>
            </div>
            <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-2 text-xs text-secondary">
                {(["temperature", "rain"] as const).map((key) => (
                    <li key={key} className="flex items-center gap-2">
                        <span aria-hidden="true" className="h-0.5 w-4 rounded-full" style={{ background: WEATHER_COLORS[key] }} />
                        {t(`dashboard.charts.${key === "temperature" ? "temperatureLine" : "rainLine"}`)}
                    </li>
                ))}
            </ul>
        </>
    );
});

export const RiverLevelChart = memo(function RiverLevelChart({ data }: { data: LevelChartRow[] }) {
    const { t, i18n } = useTranslation();
    const number = useMemo(() => new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 }), [i18n.language]);
    const compact = useMemo(() => new Intl.NumberFormat(i18n.language, { notation: "compact", maximumFractionDigits: 1 }), [i18n.language]);
    const rowsById = useMemo(() => new Map(data.map((row) => [row.id, row])), [data]);
    const statuses = useMemo(() => [...new Map(data.map((row) => [row.statusLabel, { label: row.statusLabel, color: row.color }])).values()], [data]);

    return (
        <>
            <div className="h-80 min-w-0 sm:h-[396px]" role="group" aria-label={t("dashboard.charts.levelTitle")}>
                <ResponsiveContainer width="100%" height="100%" minWidth={0} debounce={100}>
                    <BarChart data={data} layout="vertical" margin={{ top: 4, right: 62, bottom: 8, left: 0 }}>
                        <CartesianGrid stroke="var(--color-border-secondary)" strokeDasharray="3 3" horizontal={false} />
                        <XAxis
                            type="number"
                            tick={AXIS_TICK}
                            tickFormatter={(value) => compact.format(Number(value))}
                            tickLine={false}
                            axisLine={false}
                            minTickGap={24}
                            tickMargin={8}
                        />
                        <YAxis
                            type="category"
                            dataKey="id"
                            interval={0}
                            tick={AXIS_TICK}
                            tickLine={false}
                            axisLine={false}
                            width={122}
                            tickMargin={8}
                            tickFormatter={(id) => {
                                const name = rowsById.get(String(id))?.name ?? String(id);
                                return name.length > 18 ? `${name.slice(0, 17)}…` : name;
                            }}
                        />
                        <Tooltip
                            content={(props) => <ChartTooltip {...props} kind="level" />}
                            cursor={{ fill: "var(--color-bg-secondary_hover)", fillOpacity: 0.7 }}
                            isAnimationActive={false}
                        />
                        <Bar dataKey="value" name={t("dashboard.charts.levelValue")} radius={[0, 4, 4, 0]} maxBarSize={16} isAnimationActive={false}>
                            {data.map((row) => (
                                <Cell key={row.id} fill={row.color} />
                            ))}
                            <LabelList
                                dataKey="value"
                                position="right"
                                fill="var(--color-text-primary)"
                                fontSize={11}
                                formatter={(value) => number.format(Number(value))}
                            />
                        </Bar>
                    </BarChart>
                </ResponsiveContainer>
            </div>
            <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-2 text-xs text-secondary">
                {statuses.map((status) => (
                    <li key={status.label} className="flex items-center gap-2">
                        <span aria-hidden="true" className="size-2 shrink-0 rounded-full" style={{ background: status.color }} />
                        {t(status.label)}
                    </li>
                ))}
            </ul>
            <p className="mt-auto pt-3 text-xs text-tertiary">{t("dashboard.charts.levelSourceNote")}</p>
        </>
    );
});
