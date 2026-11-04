import { useCallback, useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Button } from "@/components/base/buttons/button";
import { FormAlert } from "@/components/app/form-alert";
import { ConfirmationModal } from "@/components/app/confirmation-modal";
import type { RainReading } from "@/components/app/hydro/hydro-data";
import type { ForecastDay } from "@/components/app/hydro/weather-data";
import { completeFieldAction, createFieldAction, createPropertyActivity, deleteFieldAction, deletePlot, listFieldActions, listPlots, listPropertyActivities, propertyErrorMessage, savePlot } from "@/lib/property-data";
import type { FieldAction, Plot, PropertyActivity, SavedProperty } from "@/lib/property-data";
import { useAuth } from "@/providers/auth-provider";

const input = "min-h-11 w-full rounded-lg border border-primary bg-primary px-3 py-2 text-sm text-primary outline-brand focus-visible:outline-2";
const today = () => {
    const date = new Date();
    date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
    return date.toISOString().slice(0, 10);
};
const daysAgo = (days: number) => {
    const date = new Date();
    date.setDate(date.getDate() - days);
    date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
    return date.toISOString().slice(0, 10);
};
const dateLabel = (value: string) => new Date(`${value.slice(0, 10)}T12:00:00Z`).toLocaleDateString("pt-BR", { timeZone: "UTC" });
const mm = (value: number) => `${value.toLocaleString("pt-BR", { maximumFractionDigits: 1 })} mm`;

export const PropertyOperations = ({ property, observedRain, forecast, inspectionSuggestion }: { property: SavedProperty; observedRain: RainReading | null; forecast: ForecastDay[]; inspectionSuggestion?: { propertyId: string; note: string; key: number } | null }) => {
    const { user } = useAuth();
    const canEdit = property.accessRole !== "VIEWER";
    const canDelete = property.accessRole === "ADMIN";
    const [plots, setPlots] = useState<Plot[]>([]);
    const [actions, setActions] = useState<FieldAction[]>([]);
    const [activities, setActivities] = useState<PropertyActivity[]>([]);
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const [pendingDelete, setPendingDelete] = useState<{ kind: "plot"; id: string; name: string } | { kind: "action"; id: string; name: string } | null>(null);
    const [plotId, setPlotId] = useState("");
    const [actionPlotId, setActionPlotId] = useState("");
    const [editingPlotId, setEditingPlotId] = useState("");
    const [plotName, setPlotName] = useState("");
    const [cropName, setCropName] = useState("");
    const [cropStage, setCropStage] = useState("");
    const [plantingDate, setPlantingDate] = useState("");
    const [area, setArea] = useState("");
    const [actionKind, setActionKind] = useState<"INSPECTION" | "IRRIGATION">("INSPECTION");
    const [actionTitle, setActionTitle] = useState("");
    const [responsible, setResponsible] = useState(user?.name ?? "");
    const [dueDate, setDueDate] = useState(today());
    const [sourceNote, setSourceNote] = useState("");
    const [completingId, setCompletingId] = useState("");
    const [completionNote, setCompletionNote] = useState("");
    const [completionAmount, setCompletionAmount] = useState("");
    const [waterType, setWaterType] = useState<"RAINFALL" | "IRRIGATION">("RAINFALL");
    const [waterDate, setWaterDate] = useState(today());
    const [waterFrom, setWaterFrom] = useState(daysAgo(30));
    const [waterTo, setWaterTo] = useState(today());
    const [waterAmount, setWaterAmount] = useState("");
    const [waterNote, setWaterNote] = useState("");

    useEffect(() => {
        if (!inspectionSuggestion || inspectionSuggestion.propertyId !== property.propertyId) return;
        setActionKind("INSPECTION");
        setActionTitle("Vistoriar sinal de NDVI");
        setSourceNote(inspectionSuggestion.note);
        document.getElementById("field-action-form")?.scrollIntoView({ behavior: "smooth", block: "center" });
    }, [inspectionSuggestion, property.propertyId]);

    const reload = useCallback(async () => {
        const [nextPlots, nextActions, nextActivities] = await Promise.all([
            listPlots(property.propertyId), listFieldActions(property.propertyId), listPropertyActivities(property.propertyId),
        ]);
        setPlots(nextPlots);
        setActions(nextActions);
        setActivities(nextActivities);
        setPlotId((current) => current && nextPlots.some((plot) => plot.plotId === current) ? current : nextPlots[0]?.plotId ?? "");
        setActionPlotId((current) => current && nextPlots.some((plot) => plot.plotId === current) ? current : "");
    }, [property.propertyId]);

    useEffect(() => {
        let active = true;
        setPlots([]);
        setActions([]);
        setActivities([]);
        setPlotId("");
        setActionPlotId("");
        setError("");
        Promise.all([listPlots(property.propertyId), listFieldActions(property.propertyId), listPropertyActivities(property.propertyId)])
            .then(([nextPlots, nextActions, nextActivities]) => {
                if (!active) return;
                setPlots(nextPlots);
                setActions(nextActions);
                setActivities(nextActivities);
                setPlotId(nextPlots[0]?.plotId ?? "");
            })
            .catch((reason: unknown) => { if (active) setError(propertyErrorMessage(reason, "Não foi possível carregar talhões e ações.")); });
        return () => { active = false; };
    }, [property.propertyId]);

    const run = async (operation: () => Promise<unknown>) => {
        if (busy) return false;
        setBusy(true);
        setError("");
        try { await operation(); await reload(); window.dispatchEvent(new CustomEvent("sentinela:property-activities-changed", { detail: property.propertyId })); return true; }
        catch (reason) { setError(propertyErrorMessage(reason, "Não foi possível salvar. Tente novamente.")); return false; }
        finally { setBusy(false); }
    };

    const confirmDelete = async () => {
        if (!pendingDelete || busy) return;
        const saved = await run(() => pendingDelete.kind === "plot"
            ? deletePlot(property.propertyId, pendingDelete.id) : deleteFieldAction(property.propertyId, pendingDelete.id));
        if (saved) setPendingDelete(null);
    };

    const submitPlot = (event: FormEvent) => {
        event.preventDefault();
        if (!plotName.trim() || (area && Number(area) <= 0)) return;
        void run(async () => {
            await savePlot(property.propertyId, {
                name: plotName.trim(), cropName: cropName.trim() || null, cropStage: cropStage.trim() || null,
                plantingDate: plantingDate || null, areaHectares: area ? Number(area) : null,
            }, editingPlotId || undefined);
            setEditingPlotId(""); setPlotName(""); setCropName(""); setCropStage(""); setPlantingDate(""); setArea("");
        });
    };

    const editPlot = (plot: Plot) => {
        setEditingPlotId(plot.plotId); setPlotName(plot.name); setCropName(plot.cropName ?? "");
        setCropStage(plot.cropStage ?? ""); setPlantingDate(plot.plantingDate ?? ""); setArea(plot.areaHectares?.toString() ?? "");
    };

    const submitAction = (event: FormEvent) => {
        event.preventDefault();
        if (!actionTitle.trim() || !responsible.trim() || !dueDate) return;
        void run(async () => {
            await createFieldAction(property.propertyId, {
                kind: actionKind, title: actionTitle.trim(), responsibleName: responsible.trim(), dueDate,
                plotId: actionPlotId || null, sourceNote: sourceNote.trim() || null,
            });
            setActionTitle(""); setSourceNote("");
        });
    };

    const submitCompletion = (event: FormEvent) => {
        event.preventDefault();
        if (!completionNote.trim()) return;
        void run(async () => {
            await completeFieldAction(property.propertyId, completingId, {
                completionNote: completionNote.trim(), amountMm: completionAmount ? Number(completionAmount) : null,
            });
            setCompletingId(""); setCompletionNote(""); setCompletionAmount("");
        });
    };

    const submitWater = (event: FormEvent) => {
        event.preventDefault();
        if (!plotId || !waterDate || waterAmount === "" || Number(waterAmount) < 0) return;
        void run(async () => {
            await createPropertyActivity(property.propertyId, {
                type: waterType, date: waterDate, time: null, notes: waterNote.trim() || (waterType === "RAINFALL" ? "Chuva medida na propriedade" : "Irrigação registrada"),
                amountMm: Number(waterAmount), plotId,
            });
            setWaterAmount(""); setWaterNote("");
        });
    };

    const waterRows = useMemo(() => activities.filter((row) => row.plotId === plotId && row.date >= waterFrom && row.date <= waterTo && (row.type === "IRRIGATION" || row.type === "RAINFALL")), [activities, plotId, waterFrom, waterTo]);
    const rainTotal = waterRows.filter((row) => row.type === "RAINFALL").reduce((sum, row) => sum + (row.amountMm ?? 0), 0);
    const irrigationTotal = waterRows.filter((row) => row.type === "IRRIGATION").reduce((sum, row) => sum + (row.amountMm ?? 0), 0);
    const forecastTotal = forecast.reduce((sum, day) => sum + (day.day?.totalprecip_mm ?? 0), 0);

    return <section className="grid gap-4 lg:col-span-2" aria-label="Gestão dos talhões">
        {error && <FormAlert>{error}</FormAlert>}
        <div className="grid gap-4 lg:grid-cols-2">
            <section className="rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5">
                <h2 className="text-lg font-semibold text-primary">Talhões</h2>
                <p className="mt-1 text-sm text-tertiary">Separe culturas, plantio e registros de água por área.</p>
                {canEdit && <form onSubmit={submitPlot} className="mt-4 grid gap-3 sm:grid-cols-2">
                    <label className="text-xs font-medium text-secondary">Nome do talhão<input className={input} required maxLength={120} value={plotName} onChange={(e) => setPlotName(e.target.value)} /></label>
                    <label className="text-xs font-medium text-secondary">Cultura<input className={input} maxLength={120} value={cropName} onChange={(e) => setCropName(e.target.value)} /></label>
                    <label className="text-xs font-medium text-secondary">Plantio<input className={input} type="date" max={today()} value={plantingDate} onChange={(e) => setPlantingDate(e.target.value)} /></label>
                    <label className="text-xs font-medium text-secondary">Fase<input className={input} maxLength={80} value={cropStage} onChange={(e) => setCropStage(e.target.value)} /></label>
                    <label className="text-xs font-medium text-secondary">Área (ha)<input className={input} type="number" min="0.01" step="0.01" value={area} onChange={(e) => setArea(e.target.value)} /></label>
                    <div className="flex items-end gap-2"><Button type="submit" size="sm" isLoading={busy}>{editingPlotId ? "Salvar talhão" : "Adicionar talhão"}</Button>{editingPlotId && <Button type="button" size="sm" color="secondary" onClick={() => { setEditingPlotId(""); setPlotName(""); }}>Cancelar</Button>}</div>
                </form>}
                <ul className="mt-4 divide-y divide-secondary">{plots.map((plot) => <li key={plot.plotId} className="flex items-center justify-between gap-3 py-3 text-sm">
                    <div><p className="font-semibold text-primary">{plot.name}</p><p className="text-tertiary">{plot.cropName || "Cultura não informada"}{plot.areaHectares ? ` · ${plot.areaHectares} ha` : ""}</p></div>
                    <div className="flex gap-2">{canEdit && <button type="button" className="text-brand-secondary" onClick={() => editPlot(plot)}>Editar</button>}{canDelete && <button type="button" className="text-error-primary" onClick={() => { setError(""); setPendingDelete({ kind: "plot", id: plot.plotId, name: plot.name }); }}>Excluir</button>}</div>
                </li>)}</ul>
                {plots.length === 0 && <p className="mt-3 text-sm text-tertiary">Cadastre o primeiro talhão para acompanhar água por área.</p>}
            </section>
            <section className="rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5">
                <h2 className="text-lg font-semibold text-primary">Agenda de ações</h2>
                <p className="mt-1 text-sm text-tertiary">Planeje vistorias e irrigação, com responsável e prazo.</p>
                {canEdit && <form id="field-action-form" onSubmit={submitAction} className="mt-4 grid gap-3 sm:grid-cols-2">
                    <label className="text-xs font-medium text-secondary">Ação<select className={input} value={actionKind} onChange={(e) => setActionKind(e.target.value as typeof actionKind)}><option value="INSPECTION">Vistoria</option><option value="IRRIGATION">Irrigação</option></select></label>
                    <label className="text-xs font-medium text-secondary">Talhão<select className={input} value={actionPlotId} onChange={(e) => setActionPlotId(e.target.value)}><option value="">Toda a propriedade</option>{plots.map((plot) => <option key={plot.plotId} value={plot.plotId}>{plot.name}</option>)}</select></label>
                    <label className="text-xs font-medium text-secondary sm:col-span-2">Título<input className={input} required maxLength={160} value={actionTitle} onChange={(e) => setActionTitle(e.target.value)} placeholder="Ex.: Verificar queda do NDVI" /></label>
                    <label className="text-xs font-medium text-secondary">Responsável<input className={input} required maxLength={120} value={responsible} onChange={(e) => setResponsible(e.target.value)} /></label>
                    <label className="text-xs font-medium text-secondary">Prazo<input className={input} required type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} /></label>
                    <label className="text-xs font-medium text-secondary sm:col-span-2">Origem ou observação<input className={input} maxLength={500} value={sourceNote} onChange={(e) => setSourceNote(e.target.value)} placeholder="Ex.: Alerta de monitoramento" /></label>
                    <div><Button type="submit" size="sm" isLoading={busy}>Criar ação</Button></div>
                </form>}
                <ul className="mt-4 divide-y divide-secondary">{actions.map((action) => <li key={action.actionId} className="py-3 text-sm">
                    <div className="flex items-start justify-between gap-2"><div><p className="font-semibold text-primary">{action.title} · {action.status === "DONE" ? "Concluída" : "Aberta"}</p><p className="text-tertiary">{action.responsibleName} · prazo {dateLabel(action.dueDate)}{action.plotId ? ` · ${plots.find((plot) => plot.plotId === action.plotId)?.name ?? "Talhão removido"}` : " · Propriedade"}</p>{action.sourceNote && <p className="text-tertiary">{action.sourceNote}</p>}{action.completionNote && <p className="text-secondary">Conclusão: {action.completionNote}</p>}</div><div className="flex gap-2">{canEdit && action.status === "OPEN" && <button type="button" className="text-brand-secondary" onClick={() => setCompletingId(action.actionId)}>Concluir</button>}{canDelete && <button type="button" className="text-error-primary" onClick={() => { setError(""); setPendingDelete({ kind: "action", id: action.actionId, name: action.title }); }}>Excluir</button>}</div></div>
                    {completingId === action.actionId && <form onSubmit={submitCompletion} className="mt-3 grid gap-2"><textarea className={input} required maxLength={1000} value={completionNote} onChange={(e) => setCompletionNote(e.target.value)} placeholder="O que foi feito?" />{action.kind === "IRRIGATION" && <input className={input} type="number" min="0" step="0.1" value={completionAmount} onChange={(e) => setCompletionAmount(e.target.value)} placeholder="Lâmina aplicada (mm), se medida" />}<div className="flex gap-2"><Button type="submit" size="sm" isLoading={busy}>Registrar conclusão</Button><Button type="button" size="sm" color="secondary" onClick={() => setCompletingId("")}>Cancelar</Button></div></form>}
                </li>)}</ul>
                {actions.length === 0 && <p className="mt-3 text-sm text-tertiary">Nenhuma ação planejada.</p>}
            </section>
        </div>
        <section className="rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5">
            <h2 className="text-lg font-semibold text-primary">Histórico de água por talhão</h2>
            <p className="mt-1 text-sm text-tertiary">Compare registros feitos na área com o contexto atual de chuva e previsão.</p>
            <label className="mt-4 block max-w-sm text-xs font-medium text-secondary">Talhão<select className={input} value={plotId} onChange={(e) => setPlotId(e.target.value)}><option value="">Selecione um talhão</option>{plots.map((plot) => <option key={plot.plotId} value={plot.plotId}>{plot.name}</option>)}</select></label>
            <div className="mt-3 grid max-w-xl gap-3 sm:grid-cols-2"><label className="text-xs font-medium text-secondary">De<input className={input} type="date" max={waterTo} value={waterFrom} onChange={(e) => setWaterFrom(e.target.value)} /></label><label className="text-xs font-medium text-secondary">Até<input className={input} type="date" min={waterFrom} max={today()} value={waterTo} onChange={(e) => setWaterTo(e.target.value)} /></label></div>
            {plotId && <><div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <div className="rounded-xl border border-secondary p-3 text-sm"><p className="text-tertiary">Chuva registrada</p><p className="mt-1 font-semibold text-primary">{mm(rainTotal)}</p></div>
                <div className="rounded-xl border border-secondary p-3 text-sm"><p className="text-tertiary">Irrigação registrada</p><p className="mt-1 font-semibold text-primary">{mm(irrigationTotal)}</p></div>
                <div className="rounded-xl border border-secondary p-3 text-sm"><p className="text-tertiary">Estação próxima · chuva 24 h</p><p className="mt-1 font-semibold text-primary">{observedRain ? mm(observedRain.chuvaMm24h) : "Sem leitura próxima"}</p>{observedRain && <p className="mt-1 text-xs text-tertiary">Leitura: {dateLabel(observedRain.dataUltimoDado)}</p>}</div>
                <div className="rounded-xl border border-secondary p-3 text-sm"><p className="text-tertiary">Previsão · próximos 7 dias</p><p className="mt-1 font-semibold text-primary">{forecast.length ? mm(forecastTotal) : "Indisponível"}</p></div>
            </div>
            <p className="mt-2 text-xs text-tertiary">Totais registrados consideram o período selecionado. A estação é uma leitura regional recente; a previsão é uma estimativa e não entra nos totais históricos.</p>
            {canEdit && <form onSubmit={submitWater} className="mt-4 grid gap-3 sm:grid-cols-4">
                <label className="text-xs font-medium text-secondary">Tipo<select className={input} value={waterType} onChange={(e) => setWaterType(e.target.value as typeof waterType)}><option value="RAINFALL">Chuva medida</option><option value="IRRIGATION">Irrigação</option></select></label>
                <label className="text-xs font-medium text-secondary">Data<input className={input} required type="date" max={today()} value={waterDate} onChange={(e) => setWaterDate(e.target.value)} /></label>
                <label className="text-xs font-medium text-secondary">Lâmina (mm)<input className={input} required type="number" min="0" step="0.1" value={waterAmount} onChange={(e) => setWaterAmount(e.target.value)} /></label>
                <label className="text-xs font-medium text-secondary">Observação<input className={input} maxLength={1000} value={waterNote} onChange={(e) => setWaterNote(e.target.value)} /></label>
                <div className="sm:col-span-4"><Button type="submit" size="sm" isLoading={busy}>Registrar água</Button></div>
            </form>}
            <div className="mt-4 overflow-x-auto"><table className="w-full min-w-[480px] text-sm"><thead><tr className="border-b border-secondary text-left text-tertiary"><th className="py-2">Data</th><th>Origem</th><th>Lâmina</th><th>Observação</th></tr></thead><tbody>{waterRows.map((row) => <tr key={row.activityId} className="border-b border-secondary"><td className="py-2">{dateLabel(row.date)}</td><td>{row.type === "RAINFALL" ? "Chuva medida" : "Irrigação"}</td><td>{row.amountMm == null ? "Não informada" : mm(row.amountMm)}</td><td>{row.notes}</td></tr>)}</tbody></table>{waterRows.length === 0 && <p className="mt-3 text-sm text-tertiary">Ainda não há lançamentos de água neste talhão.</p>}</div>
            </>}
        </section>
        <ConfirmationModal open={pendingDelete !== null} title={pendingDelete?.kind === "plot" ? "Excluir talhão" : "Excluir ação"}
            description={pendingDelete?.kind === "plot" ? `Excluir ${pendingDelete.name}? Os registros continuarão na propriedade, sem vínculo com o talhão.`
                : `Excluir a ação ${pendingDelete?.name ?? ""}?`}
            confirmLabel="Excluir" cancelLabel="Cancelar" onConfirm={() => void confirmDelete()}
            onCancel={() => { setPendingDelete(null); setError(""); }} busy={busy} error={error} />
    </section>;
};
