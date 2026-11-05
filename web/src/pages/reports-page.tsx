import { useEffect, useState } from "react";
import { FileDownload01 } from "@untitledui/icons";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/base/buttons/button";
import { listProperties } from "@/lib/property-data";
import type { SavedProperty } from "@/lib/property-data";
import { downloadReport, listReports, type ReportFormat, type ReportOption } from "@/lib/report-data";
import { errorKey } from "@/lib/errors";
import { FormAlert } from "@/components/app/form-alert";

type DateMode = "single" | "range";
const PROPERTY_REPORT = "sentinela_properties_activities";
const PROPERTY_ACTIVITY_SUMMARY_REPORT = "sentinela_activity_summary_by_property";
const IRRIGATION_MONTHLY_REPORT = "sentinela_irrigation_monthly_summary";
const FIELD_HISTORY_REPORT = "sentinela_field_history_by_season";
const SNIRH_RAINFALL_REPORT = "snirh_rainfall_24h_by_station";
const WEATHER_FORECAST_REPORT = "weather_forecast_by_property";
const PROPERTY_REPORT_CODES = new Set([PROPERTY_REPORT, PROPERTY_ACTIVITY_SUMMARY_REPORT, IRRIGATION_MONTHLY_REPORT, FIELD_HISTORY_REPORT]);

const today = () => {
    const value = new Date();
    value.setMinutes(value.getMinutes() - value.getTimezoneOffset());
    return value.toISOString().slice(0, 10);
};

const inputClass =
    "min-h-11 w-full rounded-lg border border-primary bg-primary px-4 py-2 text-sm text-primary outline-brand focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-50";

export const ReportsPage = () => {
    const { t } = useTranslation();
    const [reportOptions, setReportOptions] = useState<ReportOption[]>([]);
    const [reportOptionsLoading, setReportOptionsLoading] = useState(true);
    const [reportOptionsError, setReportOptionsError] = useState(false);
    const [reportCode, setReportCode] = useState(PROPERTY_REPORT);
    const [properties, setProperties] = useState<SavedProperty[]>([]);
    const [propertiesLoading, setPropertiesLoading] = useState(true);
    const [propertiesError, setPropertiesError] = useState(false);
    const [format, setFormat] = useState<ReportFormat>("pdf");
    const [dateMode, setDateMode] = useState<DateMode>("range");
    const [singleDate, setSingleDate] = useState(today());
    const [fromDate, setFromDate] = useState(today());
    const [toDate, setToDate] = useState(today());
    const [useTimeRange, setUseTimeRange] = useState(false);
    const [fromTime, setFromTime] = useState("08:00");
    const [toTime, setToTime] = useState("17:00");
    const [allProperties, setAllProperties] = useState(true);
    const [propertyIds, setPropertyIds] = useState<string[]>([]);
    const [weatherPropertyId, setWeatherPropertyId] = useState("");
    const [forecastDays, setForecastDays] = useState(3);
    const [stateFilter, setStateFilter] = useState("");
    const [onlyAttention, setOnlyAttention] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const controller = new AbortController();
        listReports(controller.signal)
            .then((result) => {
                if (!controller.signal.aborted) setReportOptions(result);
            })
            .catch(() => {
                if (!controller.signal.aborted) setReportOptionsError(true);
            })
            .finally(() => {
                if (!controller.signal.aborted) setReportOptionsLoading(false);
            });
        listProperties(controller.signal)
            .then((result) => {
                if (!controller.signal.aborted) setProperties(result);
            })
            .catch(() => {
                if (!controller.signal.aborted) setPropertiesError(true);
            })
            .finally(() => {
                if (!controller.signal.aborted) setPropertiesLoading(false);
            });
        return () => controller.abort();
    }, []);

    const effectiveFrom = dateMode === "single" ? singleDate : fromDate;
    const effectiveTo = dateMode === "single" ? singleDate : toDate;
    const isPropertyReport = PROPERTY_REPORT_CODES.has(reportCode);
    const isWeatherReport = reportCode.startsWith("weather_");
    const reportInfoKey = reportCode === PROPERTY_ACTIVITY_SUMMARY_REPORT
        ? "reports.activitySummaryInfoText"
        : reportCode === FIELD_HISTORY_REPORT
            ? "reports.fieldHistoryInfoText"
        : reportCode === IRRIGATION_MONTHLY_REPORT
            ? "reports.irrigationSummaryInfoText"
            : reportCode === SNIRH_RAINFALL_REPORT
                ? "reports.rainfallInfoText"
            : isWeatherReport ? "reports.weatherInfoText"
            : isPropertyReport ? "reports.infoText" : "reports.snirhInfoText";
    const invalidDates = isPropertyReport && (!effectiveFrom || !effectiveTo || effectiveFrom > effectiveTo);
    const invalidTimes = isPropertyReport && useTimeRange && (!fromTime || !toTime || fromTime > toTime);
    const invalidPropertySelection = isPropertyReport && !allProperties && propertyIds.length === 0;
    const invalidWeatherSelection = isWeatherReport && (!weatherPropertyId || !properties.some((property) => property.propertyId === weatherPropertyId) || propertiesLoading || propertiesError);

    const onSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (busy || reportOptionsLoading || reportOptionsError || !reportOptions.some((option) => option.code === reportCode) || invalidDates || invalidTimes || invalidPropertySelection || invalidWeatherSelection || (isPropertyReport && !allProperties && (propertiesLoading || propertiesError))) return;

        setBusy(true);
        setError(null);
        try {
            const parameters: Record<string, string | number | boolean | string[] | null> = isPropertyReport
                ? {
                    allProperties,
                    propertyIds: allProperties ? [] : propertyIds,
                    fromDate: effectiveFrom,
                    toDate: effectiveTo,
                    fromTime: useTimeRange ? fromTime : null,
                    toTime: useTimeRange ? toTime : null,
                }
                : isWeatherReport
                    ? { propertyId: weatherPropertyId, ...(reportCode === WEATHER_FORECAST_REPORT ? { days: forecastDays } : {}) }
                    : { state: stateFilter || null, onlyAttention };
            const { blob, filename } = await downloadReport(reportCode, { format, parameters });
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.href = url;
            link.download = filename || `${reportCode}.${format}`;
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (reason) {
            setError(t(errorKey(reason)));
            return;
        } finally {
            setBusy(false);
        }
    };

    return (
        <div data-reports-page className="container-page py-8 lg:py-12">
            <header className="max-w-3xl pb-2">
                <h1 className="text-3xl font-semibold tracking-tight text-primary sm:text-4xl">{t("reports.title")}</h1>
                <p className="mt-3 text-base leading-relaxed text-tertiary">{t("reports.subtitle")}</p>
            </header>

            <div className="mt-8 grid gap-5 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
                <form onSubmit={(event) => void onSubmit(event)} className="overflow-hidden rounded-[18px] bg-primary ring-1 ring-secondary">
                    <div className="border-b border-secondary px-5 py-5 sm:px-6">
                        <div className="flex items-start gap-3">
                            <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand-secondary text-brand-primary ring-1 ring-inset ring-brand-secondary">
                                <FileDownload01 className="size-5" aria-hidden="true" />
                            </span>
                            <div>
                                <h2 className="text-lg font-semibold tracking-tight text-primary">{t("reports.generatorTitle")}</h2>
                                <p className="mt-1 text-sm text-tertiary">{t("reports.generatorSubtitle")}</p>
                            </div>
                        </div>
                    </div>

                    <div className="grid gap-5 px-5 py-5 sm:px-6">
                        <label className="flex flex-col gap-1.5 text-sm font-medium text-secondary">
                            {t("reports.dataLabel")}
                            <select className={inputClass} value={reportCode} onChange={(event) => setReportCode(event.target.value)} disabled={reportOptionsLoading}>
                                {reportOptions.map((option) => (
                                    <option key={option.code} value={option.code}>
                                        {t(`reports.options.${option.code}`, { defaultValue: option.title })}
                                    </option>
                                ))}
                            </select>
                            {reportOptionsLoading && <span className="text-xs font-normal text-tertiary">{t("reports.loadingReports")}</span>}
                            <FormAlert>{reportOptionsError && !reportOptionsLoading ? t("reports.reportsError") : null}</FormAlert>
                        </label>

                        {isPropertyReport ? <>
                        <div className="grid gap-2">
                            <label className="inline-flex items-center gap-2 text-sm font-medium text-secondary">
                                <input type="checkbox" checked={allProperties} onChange={(event) => {
                                    setAllProperties(event.target.checked);
                                    if (event.target.checked) setPropertyIds([]);
                                }} />
                                {t("reports.allProperties")}
                            </label>
                            {!allProperties && <label className="flex flex-col gap-1.5 text-sm font-medium text-secondary">
                                {t("reports.propertyLabel")}
                                <select multiple size={Math.min(Math.max(properties.length, 3), 6)} className={inputClass} value={propertyIds} onChange={(event) => setPropertyIds(Array.from(event.target.selectedOptions, (option) => option.value))} disabled={propertiesLoading || propertiesError}>
                                    {properties.map((property) => (
                                        <option key={property.propertyId} value={property.propertyId}>
                                            {property.name} · {property.city}/{property.state}
                                        </option>
                                    ))}
                                </select>
                                <span className="text-xs font-normal text-tertiary">{t("reports.selectPropertiesHint")}</span>
                            </label>}
                            {propertiesLoading && <span className="text-xs font-normal text-tertiary">{t("reports.loadingProperties")}</span>}
                            <FormAlert>{propertiesError ? t("reports.propertiesError") : null}</FormAlert>
                            <FormAlert type="info">{invalidPropertySelection ? t("reports.selectAtLeastOneProperty") : null}</FormAlert>
                        </div>

                        <fieldset className="grid gap-3">
                            <legend className="mb-1 text-sm font-medium text-secondary">{t("reports.periodLabel")}</legend>
                            <div className="flex flex-wrap gap-x-5 gap-y-2 text-sm text-secondary">
                                <label className="inline-flex items-center gap-2">
                                    <input type="radio" name="dateMode" checked={dateMode === "range"} onChange={() => setDateMode("range")} />
                                    {t("reports.range")}
                                </label>
                                <label className="inline-flex items-center gap-2">
                                    <input type="radio" name="dateMode" checked={dateMode === "single"} onChange={() => setDateMode("single")} />
                                    {t("reports.singleDate")}
                                </label>
                            </div>
                            {dateMode === "single" ? (
                                <label className="flex max-w-sm flex-col gap-1.5 text-sm font-medium text-secondary">
                                    {t("reports.date")}
                                    <input required type="date" className={inputClass} value={singleDate} onChange={(event) => setSingleDate(event.target.value)} />
                                </label>
                            ) : (
                                <div className="grid gap-4 sm:grid-cols-2">
                                    <label className="flex flex-col gap-1.5 text-sm font-medium text-secondary">
                                        {t("reports.fromDate")}
                                        <input required type="date" className={inputClass} value={fromDate} onChange={(event) => setFromDate(event.target.value)} />
                                    </label>
                                    <label className="flex flex-col gap-1.5 text-sm font-medium text-secondary">
                                        {t("reports.toDate")}
                                        <input required type="date" className={inputClass} value={toDate} onChange={(event) => setToDate(event.target.value)} />
                                    </label>
                                </div>
                            )}
                            <FormAlert type="info">{invalidDates ? t("reports.invalidDates") : null}</FormAlert>
                        </fieldset>

                        <div className="grid gap-3">
                            <label className="inline-flex items-center gap-2 text-sm font-medium text-secondary">
                                <input type="checkbox" checked={useTimeRange} onChange={(event) => setUseTimeRange(event.target.checked)} />
                                {t("reports.filterByTime")}
                            </label>
                            {useTimeRange && (
                                <div className="grid gap-4 sm:grid-cols-2">
                                    <label className="flex flex-col gap-1.5 text-sm font-medium text-secondary">
                                        {t("reports.fromTime")}
                                        <input required type="time" className={inputClass} value={fromTime} onChange={(event) => setFromTime(event.target.value)} />
                                    </label>
                                    <label className="flex flex-col gap-1.5 text-sm font-medium text-secondary">
                                        {t("reports.toTime")}
                                        <input required type="time" className={inputClass} value={toTime} onChange={(event) => setToTime(event.target.value)} />
                                    </label>
                                    <p className="text-xs leading-5 text-tertiary sm:col-span-2">{t(reportCode === FIELD_HISTORY_REPORT ? "reports.fieldHistoryTimeNote" : "reports.timeNote")}</p>
                                    <FormAlert type="info">{invalidTimes ? t("reports.invalidTimes") : null}</FormAlert>
                                </div>
                            )}
                        </div>
                        </> : isWeatherReport ? <div className="grid gap-3">
                            <label className="flex max-w-sm flex-col gap-1.5 text-sm font-medium text-secondary">
                                {t("reports.propertyLabel")}
                                <select required className={inputClass} value={weatherPropertyId} onChange={(event) => setWeatherPropertyId(event.target.value)} disabled={propertiesLoading || propertiesError}>
                                    <option value="">{t("reports.selectWeatherProperty")}</option>
                                    {properties.map((property) => <option key={property.propertyId} value={property.propertyId}>{property.name} · {property.city}/{property.state}</option>)}
                                </select>
                            </label>
                            {propertiesLoading && <span className="text-xs text-tertiary">{t("reports.loadingProperties")}</span>}
                            <FormAlert>{propertiesError ? t("reports.propertiesError") : null}</FormAlert>
                            {reportCode === WEATHER_FORECAST_REPORT && <label className="flex max-w-sm flex-col gap-1.5 text-sm font-medium text-secondary">
                                {t("reports.forecastDays")}
                                <select className={inputClass} value={forecastDays} onChange={(event) => setForecastDays(Number(event.target.value))}>
                                    {[1, 2, 3].map((days) => <option key={days} value={days}>{days}</option>)}
                                </select>
                            </label>}
                        </div> : <div className="grid gap-3">
                            <label className="flex max-w-sm flex-col gap-1.5 text-sm font-medium text-secondary">
                                {t("reports.stateFilter")}
                                <select className={inputClass} value={stateFilter} onChange={(event) => setStateFilter(event.target.value)}>
                                    <option value="">{t("reports.allStates")}</option>
                                    {["AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"].map((state) => <option key={state} value={state}>{state}</option>)}
                                </select>
                            </label>
                            <label className="inline-flex items-center gap-2 text-sm font-medium text-secondary">
                                <input type="checkbox" checked={onlyAttention} onChange={(event) => setOnlyAttention(event.target.checked)} />
                                {t("reports.onlyAttention")}
                            </label>
                        </div>}

                        <label className="flex max-w-sm flex-col gap-1.5 text-sm font-medium text-secondary">
                            {t("reports.formatLabel")}
                            <select className={inputClass} value={format} onChange={(event) => setFormat(event.target.value as ReportFormat)}>
                                <option value="pdf">PDF</option>
                                <option value="html">HTML</option>
                                <option value="xls">Excel (.xlsx)</option>
                                <option value="odf">OpenDocument (.odt)</option>
                                <option value="csv">CSV</option>
                                <option value="rtf">RTF (Word)</option>
                                <option value="txt">{t("reports.text")}</option>
                            </select>
                        </label>

                        <FormAlert>{error}</FormAlert>
                    </div>

                    <footer className="flex flex-wrap items-center justify-end gap-3 border-t border-secondary bg-secondary px-5 py-3 sm:px-6">
                        <Button type="submit" isLoading={busy} isDisabled={busy || reportOptionsLoading || reportOptionsError || !reportOptions.some((option) => option.code === reportCode) || invalidDates || invalidTimes || invalidPropertySelection || invalidWeatherSelection || (isPropertyReport && !allProperties && (propertiesLoading || propertiesError))} iconLeading={FileDownload01}>
                            {busy ? t("reports.generating") : t("reports.generate")}
                        </Button>
                    </footer>
                </form>

                <aside className="rounded-[18px] bg-primary p-6 ring-1 ring-secondary">
                    <h2 className="text-base font-semibold tracking-tight text-primary">{t("reports.infoTitle")}</h2>
                    <p className="mt-2 text-sm leading-6 text-tertiary">{t(reportInfoKey)}</p>
                    <p className="mt-3 text-xs leading-5 text-tertiary">{t(isPropertyReport ? "reports.securityNote" : isWeatherReport ? "reports.weatherSourceNote" : "reports.snirhSecurityNote")}</p>
                </aside>
            </div>
        </div>
    );
};
