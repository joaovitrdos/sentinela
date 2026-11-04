import { FormAlert } from "@/components/app/form-alert";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AlertCircle, RefreshCw01 } from "@untitledui/icons";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Button } from "@/components/base/buttons/button";
import { Select } from "@/components/base/select/select";
import { loadPropertyImage, loadPropertyMonitoring, propertyErrorMessage } from "@/lib/property-data";
import type { PropertyImageOverlay, PropertyMonitoringData, SavedProperty } from "@/lib/property-data";

const fieldClass =
    "min-w-0 rounded-lg border border-primary bg-primary px-3 py-2 text-sm text-primary outline-brand focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-50";
const formatNumber = (value: number | null | undefined, digits = 2) =>
    value != null && Number.isFinite(value) ? value.toLocaleString("pt-BR", { maximumFractionDigits: digits, minimumFractionDigits: digits }) : "—";
const formatPercentage = (value: number | null | undefined) => (value != null && Number.isFinite(value) ? `${formatNumber(value, 1)}%` : "—");
const formatDate = (value: string) => new Date(`${value.slice(0, 10)}T12:00:00Z`).toLocaleDateString("pt-BR", { timeZone: "UTC" });
const dateDaysAgo = (days: number) => {
    const date = new Date();
    date.setUTCDate(date.getUTCDate() - days);
    return date.toISOString().slice(0, 10);
};

const NdviChart = memo(function NdviChart({ data }: { data: PropertyMonitoringData["observations"] }) {
    return (
        <div className="h-64 min-w-0" role="group" aria-label="Evolução do NDVI médio da propriedade">
            <ResponsiveContainer width="100%" height="100%" minWidth={0} debounce={100}>
                <LineChart data={data} margin={{ top: 12, right: 12, bottom: 8, left: -8 }}>
                    <CartesianGrid stroke="var(--color-border-secondary)" strokeDasharray="3 3" vertical={false} />
                    <XAxis
                        dataKey="date"
                        tickFormatter={(date: string) => formatDate(date).slice(0, 5)}
                        tick={{ fill: "var(--color-text-tertiary)", fontSize: 11 }}
                        tickLine={false}
                        axisLine={false}
                        minTickGap={30}
                    />
                    <YAxis
                        domain={[-1, 1]}
                        width={48}
                        tickFormatter={(value: number) => formatNumber(value, 1)}
                        tick={{ fill: "var(--color-text-tertiary)", fontSize: 11 }}
                        tickLine={false}
                        axisLine={false}
                    />
                    <Tooltip
                        isAnimationActive={false}
                        content={({ active, payload }) => {
                            const row = payload?.[0]?.payload as PropertyMonitoringData["observations"][number] | undefined;
                            if (!active || !row) return null;
                            return (
                                <div className="rounded-xl border border-secondary bg-primary p-3 text-xs text-secondary shadow-lg">
                                    <p className="font-semibold text-primary">{formatDate(row.date)}</p>
                                    <p className="mt-2">
                                        NDVI médio: <strong>{formatNumber(row.meanNdvi)}</strong>
                                    </p>
                                    <p className="mt-1">
                                        Mín. {formatNumber(row.minNdvi)} · Máx. {formatNumber(row.maxNdvi)}
                                    </p>
                                    <p className="mt-1">Pixels úteis no contorno: {formatPercentage(row.validPixelPercentage)}</p>
                                </div>
                            );
                        }}
                    />
                    <Line
                        type="linear"
                        dataKey="meanNdvi"
                        name="NDVI médio"
                        stroke="var(--color-utility-green-500)"
                        strokeWidth={2.5}
                        dot={{ r: 3 }}
                        activeDot={{ r: 5 }}
                        connectNulls={false}
                        isAnimationActive={false}
                    />
                </LineChart>
            </ResponsiveContainer>
        </div>
    );
});

export const PropertyMonitoring = ({ property, onOverlay, onPlanInspection }: { property: SavedProperty | null; onOverlay: (overlay: PropertyImageOverlay | null) => void; onPlanInspection?: (note: string) => void }) => {
    const [from, setFrom] = useState(() => dateDaysAgo(29));
    const [to, setTo] = useState(() => dateDaysAgo(0));
    const [periodDays, setPeriodDays] = useState<number | null>(30);
    const [clouds, setClouds] = useState(40);
    const [refresh, setRefresh] = useState(0);
    const [response, setResponse] = useState<{ key: string; data: PropertyMonitoringData } | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [date, setDate] = useState("");
    const [imageLoading, setImageLoading] = useState(false);
    const [imageError, setImageError] = useState<string | null>(null);
    const lastQueryAt = useRef(0);
    const propertyId = property?.propertyId;
    const requestKey = `${propertyId}|${property?.updatedAt ?? ""}|${from}|${to}|${clouds}|${refresh}`;
    const data = response?.key === requestKey ? (response?.data ?? null) : null;
    const hasGeometry = !!property?.geometry;
    const invalidPeriod = !from || !to || from > to || to > dateDaysAgo(0) || (Date.parse(to) - Date.parse(from)) / 86_400_000 >= 90;
    const refreshMonitoring = useCallback(() => {
        if (periodDays) {
            setFrom(dateDaysAgo(periodDays - 1));
            setTo(dateDaysAgo(0));
        }
        setRefresh((value) => value + 1);
    }, [periodDays]);

    useEffect(() => {
        setResponse(null);
        setDate("");
        setError(null);
        if (!propertyId || !hasGeometry || invalidPeriod) {
            setLoading(false);
            return;
        }
        const controller = new AbortController();
        lastQueryAt.current = Date.now();
        setLoading(true);
        loadPropertyMonitoring(propertyId, from, to, clouds, controller.signal)
            .then((result) => {
                if (controller.signal.aborted) return;
                setResponse({ key: requestKey, data: result });
                setDate(result.latestDate ?? result.observations.at(-1)?.date ?? "");
            })
            .catch((reason: unknown) => {
                if (!controller.signal.aborted)
                    setError(propertyErrorMessage(reason, "Não foi possível consultar o monitoramento da propriedade. Tente novamente."));
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [propertyId, hasGeometry, from, to, clouds, refresh, invalidPeriod, requestKey]);

    useEffect(() => {
        if (!propertyId || !hasGeometry || invalidPeriod) return;
        const refreshVisible = () => {
            if (document.visibilityState !== "visible" || Date.now() - lastQueryAt.current < 3_600_000) return;
            lastQueryAt.current = Date.now();
            refreshMonitoring();
        };
        const timer = window.setInterval(refreshVisible, 300_000);
        document.addEventListener("visibilitychange", refreshVisible);
        window.addEventListener("online", refreshVisible);
        return () => {
            window.clearInterval(timer);
            document.removeEventListener("visibilitychange", refreshVisible);
            window.removeEventListener("online", refreshVisible);
        };
    }, [propertyId, hasGeometry, invalidPeriod, refreshMonitoring]);

    useEffect(() => {
        onOverlay(null);
        setImageError(null);
        if (!propertyId || !data?.configured || !date || !data.bounds || data.bounds.length !== 4) {
            setImageLoading(false);
            return;
        }
        const controller = new AbortController();
        const bounds = data.bounds;
        let objectUrl: string | null = null;
        setImageLoading(true);
        loadPropertyImage(propertyId, date, clouds, controller.signal)
            .then((blob) => {
                if (controller.signal.aborted) return;
                objectUrl = URL.createObjectURL(blob);
                onOverlay({ url: objectUrl, bounds, date });
            })
            .catch((reason: unknown) => {
                if (!controller.signal.aborted)
                    setImageError(
                        propertyErrorMessage(reason, "A imagem NDVI não está disponível para esta leitura. Escolha outra data ou atualize mais tarde."),
                    );
            })
            .finally(() => {
                if (!controller.signal.aborted) setImageLoading(false);
            });
        return () => {
            controller.abort();
            onOverlay(null);
            if (objectUrl) URL.revokeObjectURL(objectUrl);
        };
    }, [propertyId, data, date, clouds, onOverlay]);

    const selectedObservation = useMemo(() => data?.observations.find((observation) => observation.date === date) ?? null, [data, date]);
    const latestObservation = data?.observations.at(-1);
    const ndviSignal = useMemo(() => {
        const observations = data?.observations ?? [];
        if (observations.length < 3) return { status: "insufficient" as const, baseline: null, dropPercent: null };
        const latest = observations[observations.length - 1];
        const history = observations.slice(0, -1);
        const baseline = history.reduce((sum, item) => sum + item.meanNdvi, 0) / history.length;
        const dropPercent = baseline > 0 ? ((baseline - latest.meanNdvi) / baseline) * 100 : null;
        if (latest.validPixelPercentage == null || latest.validPixelPercentage < 50)
            return { status: "low-quality" as const, baseline, dropPercent };
        return { status: dropPercent != null && dropPercent >= 15 && baseline - latest.meanNdvi >= 0.04 ? "inspect" as const : "stable" as const, baseline, dropPercent };
    }, [data]);
    const preset = periodDays ? String(periodDays) : "custom";

    return (
        <section className="min-w-0 rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5" aria-labelledby="property-monitoring-title">
            <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                    <h2 id="property-monitoring-title" className="text-lg font-semibold text-primary">
                        Monitoramento da vegetação
                    </h2>
                    <p className="mt-1 text-sm text-tertiary">Sentinel-2 L2A · NDVI somente no contorno salvo · Datas das imagens em UTC.</p>
                </div>
                <Button
                    size="sm"
                    color="secondary"
                    iconLeading={RefreshCw01}
                    onClick={refreshMonitoring}
                    isLoading={loading}
                    isDisabled={!propertyId || !hasGeometry || invalidPeriod}
                >
                    Atualizar
                </Button>
            </div>

            {!property ? (
                <p className="mt-5 rounded-xl bg-secondary p-4 text-sm text-tertiary">
                    Selecione uma propriedade salva para acompanhar a vegetação. Ao cadastrar ou editar uma área, salve o contorno para iniciar a consulta.
                </p>
            ) : !hasGeometry ? (
                <p className="mt-5 rounded-xl bg-secondary p-4 text-sm text-tertiary">
                    Esta propriedade ainda não tem um contorno cadastrado. Clique em Editar e desenhe a área no mapa.
                </p>
            ) : (
                <>
                    <div className="mt-5 flex flex-wrap items-end gap-3 border-b border-secondary pb-5">
                        <Select
                            size="sm"
                            label="Período"
                            className="w-full min-w-36 sm:w-44"
                            selectedKey={preset}
                            onSelectionChange={(key) => {
                                if (key == null) return;
                                const days = key === "custom" ? null : Number(key);
                                setPeriodDays(days);
                                if (days) {
                                    setFrom(dateDaysAgo(days - 1));
                                    setTo(dateDaysAgo(0));
                                }
                            }}
                            items={[
                                { id: "7", label: "Últimos 7 dias" },
                                { id: "30", label: "Últimos 30 dias" },
                                { id: "90", label: "Últimos 90 dias" },
                                { id: "custom", label: "Personalizado" },
                            ]}
                        >
                            {(item) => <Select.Item id={item.id} label={item.label} />}
                        </Select>
                        <label className="flex min-w-36 flex-col gap-1.5 text-xs font-medium text-secondary">
                            De
                            <input
                                type="date"
                                className={fieldClass}
                                value={from}
                                max={to || dateDaysAgo(0)}
                                onChange={(event) => {
                                    setPeriodDays(null);
                                    setFrom(event.target.value);
                                }}
                            />
                        </label>
                        <label className="flex min-w-36 flex-col gap-1.5 text-xs font-medium text-secondary">
                            Até
                            <input
                                type="date"
                                className={fieldClass}
                                value={to}
                                min={from}
                                max={dateDaysAgo(0)}
                                onChange={(event) => {
                                    setPeriodDays(null);
                                    setTo(event.target.value);
                                }}
                            />
                        </label>
                        <Select
                            size="sm"
                            label="Nuvens na cena, até"
                            className="w-full min-w-40 sm:w-44"
                            selectedKey={String(clouds)}
                            onSelectionChange={(key) => {
                                if (key != null) setClouds(Number(key));
                            }}
                            items={[
                                { id: "20", label: "20%" },
                                { id: "40", label: "40%" },
                                { id: "60", label: "60%" },
                                { id: "100", label: "100%" },
                            ]}
                        >
                            {(item) => <Select.Item id={item.id} label={item.label} />}
                        </Select>
                    </div>
                    {invalidPeriod && (
                        <FormAlert>
                            Escolha um intervalo de até 90 dias, sem datas futuras.
                        </FormAlert>
                    )}
                    {loading && (
                        <p role="status" className="mt-5 text-sm text-tertiary">
                            Consultando as passagens disponíveis do satélite…
                        </p>
                    )}
                    {error && (
                        <FormAlert>
                            {error}
                        </FormAlert>
                    )}
                    {data?.status === "NOT_CONFIGURED" && (
                        <div className="mt-5 flex items-start gap-3 rounded-xl border border-secondary bg-secondary p-4 text-sm text-secondary">
                            <AlertCircle className="mt-0.5 size-5 shrink-0 text-tertiary" aria-hidden="true" />
                            <div>
                                <p className="font-semibold text-primary">Conecte o Copernicus para começar</p>
                                <p className="mt-1 leading-6">
                                    {data.message ||
                                        "As credenciais da integração ainda não foram configuradas no servidor. Sua propriedade e seu contorno já estão salvos."}
                                </p>
                                <p className="mt-2 text-xs text-tertiary">
                                    O responsável pelo sistema deve concluir a configuração da conta Copernicus. As credenciais são mantidas no servidor.
                                </p>
                            </div>
                        </div>
                    )}
                    {data && data.configured && data.observations.length === 0 && (
                        <p className="mt-5 rounded-xl bg-secondary p-4 text-sm leading-6 text-tertiary">
                            {data.message ||
                                "Nenhuma leitura útil foi encontrada neste período. Amplie o intervalo ou o limite de nuvens. A disponibilidade depende das passagens do satélite e das condições da imagem."}
                        </p>
                    )}
                    {!!data?.observations.length && (
                        <>
                            <aside className={`mt-5 rounded-xl border p-4 ${ndviSignal.status === "inspect" ? "border-secondary bg-warning-primary" : "border-secondary bg-secondary"}`} aria-label="Sinal de vistoria baseado no histórico de NDVI">
                                <h3 className="text-sm font-semibold text-primary">Sinal para vistoria · não é alerta automático</h3>
                                {ndviSignal.status === "insufficient" ? (
                                    <p className="mt-1.5 text-sm leading-6 text-secondary">Ainda não há três observações válidas para comparar a leitura com o histórico desta área. O contexto atual é {property.cropName || "cultura não informada"}{property.cropStage ? ` · ${property.cropStage}` : ""}.</p>
                                ) : ndviSignal.status === "low-quality" ? (
                                    <p className="mt-1.5 text-sm leading-6 text-secondary">A cobertura útil da última imagem é inferior a 50% ou não foi informada. Aguarde uma passagem com melhor cobertura antes de interpretar a mudança para {property.cropName || "esta cultura"}.</p>
                                ) : ndviSignal.status === "inspect" ? (
                                    <p className="mt-1.5 text-sm leading-6 text-secondary">O NDVI mais recente está {formatPercentage(ndviSignal.dropPercent)} abaixo da média das observações anteriores desta área ({formatNumber(ndviSignal.baseline)}). Considere vistoriar {property.plotName || property.name}, em {property.cropName || "cultura não informada"}{property.cropStage ? ` na fase ${property.cropStage}` : ""}, e registrar o que encontrar.</p>
                                ) : (
                                    <p className="mt-1.5 text-sm leading-6 text-secondary">A leitura mais recente não mostra uma queda relevante frente à média histórica desta área ({formatNumber(ndviSignal.baseline)}). A comparação considera {property.cropName || "a cultura não informada"}{property.cropStage ? ` · ${property.cropStage}` : ""}, mas não substitui avaliação agronômica.</p>
                                )}
                                <p className="mt-2 text-xs leading-5 text-tertiary">Comparação com o histórico desta propriedade; limiar indicativo de queda de 15% e 0,04 NDVI. Mudanças no NDVI não identificam a causa e podem refletir cultura, fase, solo, água, nuvens ou época do ano.</p>
                                {property.accessRole !== "VIEWER" && ndviSignal.status === "inspect" && onPlanInspection && <button type="button" className="mt-3 text-sm font-semibold text-brand-secondary" onClick={() => onPlanInspection(`Queda indicativa de ${formatPercentage(ndviSignal.dropPercent)} no NDVI da propriedade; verificar em campo.`)}>Planejar vistoria deste sinal</button>}
                            </aside>
                            {data.status === "PARTIAL" && data.message && (
                                <p role="status" className="mt-4 rounded-xl bg-secondary p-3 text-sm text-tertiary">
                                    {data.message}
                                </p>
                            )}
                            <div className="mt-5 grid gap-3 sm:grid-cols-3">
                                <div className="rounded-xl border border-secondary p-4">
                                    <p className="text-xs font-medium text-tertiary">NDVI da última leitura</p>
                                    <p className="mt-2 text-2xl font-semibold text-primary tabular-nums">{formatNumber(latestObservation?.meanNdvi)}</p>
                                    <p className="mt-1 text-xs text-tertiary">{latestObservation ? formatDate(latestObservation.date) : "Sem leitura"}</p>
                                </div>
                                <div className="rounded-xl border border-secondary p-4">
                                    <p className="text-xs font-medium text-tertiary">Variação entre as últimas leituras</p>
                                    <p className="mt-2 text-2xl font-semibold text-primary tabular-nums">
                                        {data.changeNdvi == null ? "—" : `${data.changeNdvi > 0 ? "+" : ""}${formatNumber(data.changeNdvi)}`}
                                    </p>
                                    <p className="mt-1 text-xs text-tertiary">
                                        {data.previousMeanNdvi == null || !data.previousDate
                                            ? "São necessárias duas leituras úteis"
                                            : `Desde ${formatDate(data.previousDate)} · referência ${formatNumber(data.previousMeanNdvi)}`}
                                    </p>
                                </div>
                                <div className="rounded-xl border border-secondary p-4">
                                    <p className="text-xs font-medium text-tertiary">Pixels úteis no contorno</p>
                                    <p className="mt-2 text-2xl font-semibold text-primary tabular-nums">
                                        {formatPercentage(selectedObservation?.validPixelPercentage)}
                                    </p>
                                    <p className="mt-1 text-xs text-tertiary">Pixels da área com NDVI válido na data selecionada</p>
                                </div>
                            </div>
                            <div className="mt-5 flex flex-wrap items-end justify-between gap-3">
                                <div>
                                    <h3 className="text-sm font-semibold text-primary">Evolução do NDVI</h3>
                                    <p className="mt-1 text-xs text-tertiary">{data.observations.length} leituras úteis no período</p>
                                </div>
                                <Select
                                    size="sm"
                                    label="Imagem sobre o mapa"
                                    className="w-full min-w-44 sm:w-48"
                                    selectedKey={date || null}
                                    onSelectionChange={(key) => {
                                        if (key != null) setDate(String(key));
                                    }}
                                    items={data.observations.map((observation) => ({ id: observation.date, label: formatDate(observation.date) }))}
                                >
                                    {(item) => <Select.Item id={item.id} label={item.label} />}
                                </Select>
                            </div>
                            <div className="mt-3">
                                <NdviChart data={data.observations} />
                            </div>
                            {imageLoading && (
                                <p role="status" className="mt-2 text-xs text-tertiary">
                                    Preparando a imagem NDVI recortada para o mapa…
                                </p>
                            )}
                            {imageError && (
                                <FormAlert>
                                    {imageError}
                                </FormAlert>
                            )}
                            <p className="mt-3 border-t border-secondary pt-3 text-xs leading-5 text-tertiary">
                                {data.source || "Copernicus Data Space · Sentinel-2 L2A"}
                                {data.resolutionMeters ? ` · Resolução aproximada: ${formatNumber(data.resolutionMeters, 0)} m` : ""}. Nuvens e pixels sem dados
                                são excluídos. O NDVI indica vigor da vegetação; não mede diretamente a umidade do solo e não identifica a causa de uma
                                alteração.
                            </p>
                        </>
                    )}
                </>
            )}
        </section>
    );
};
