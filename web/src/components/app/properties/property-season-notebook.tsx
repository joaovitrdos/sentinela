import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Button } from "@/components/base/buttons/button";
import { FormAlert } from "@/components/app/form-alert";
import { closePlotSeason, createPlotSeason, createPropertyActivity, listActivityPhotos, listPlotSeasons, listPlots, listPropertyActivities, listTeamAssignees, loadActivityPhoto, loadFieldSettings, propertyErrorMessage, uploadActivityPhoto } from "@/lib/property-data";
import type { FieldSettings, Plot, PlotSeason, PropertyActivity, PropertyActivityType, SavedProperty, TeamAssignee } from "@/lib/property-data";

const field = "min-h-11 w-full rounded-lg border border-primary bg-primary px-3 py-2 text-sm text-primary outline-brand focus-visible:outline-2";
const today = () => {
    const date = new Date();
    date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
    return date.toISOString().slice(0, 10);
};
const labels: Record<PropertyActivityType, string> = {
    IRRIGATION: "Irrigação", RAINFALL: "Chuva", INSPECTION: "Vistoria", INCIDENT: "Ocorrência",
    PLANTING: "Plantio", FERTILIZATION: "Adubação", SPRAYING: "Aplicação", HARVEST: "Colheita", OTHER: "Outra operação",
};

function Photos({ propertyId, activityId, canEdit }: { propertyId: string; activityId: string; canEdit: boolean }) {
    const [shown, setShown] = useState(false);
    const [images, setImages] = useState<Array<{ name: string; url: string }>>([]);
    const [error, setError] = useState("");
    const [upload, setUpload] = useState<File | null>(null);
    useEffect(() => () => images.forEach(({ url }) => URL.revokeObjectURL(url)), [images]);
    const open = async () => {
        if (shown) { setShown(false); return; }
        setError("");
        try {
            const rows = await listActivityPhotos(propertyId, activityId);
            const loaded = await Promise.all(rows.map(async (photo) => ({
                name: photo.fileName,
                url: URL.createObjectURL(await loadActivityPhoto(propertyId, activityId, photo.photoId)),
            })));
            setImages(loaded);
            setShown(true);
        } catch (reason) { setError(propertyErrorMessage(reason, "Não foi possível carregar as fotos.")); }
    };
    const addPhoto = async () => {
        if (!upload) return;
        setError("");
        try {
            await uploadActivityPhoto(propertyId, activityId, upload);
            setUpload(null);
            const rows = await listActivityPhotos(propertyId, activityId);
            const loaded = await Promise.all(rows.map(async (photo) => ({
                name: photo.fileName,
                url: URL.createObjectURL(await loadActivityPhoto(propertyId, activityId, photo.photoId)),
            })));
            setImages([]);
            setImages(loaded);
            setShown(true);
        } catch (reason) { setError(propertyErrorMessage(reason, "Não foi possível enviar a foto.")); }
    };
    return <div className="mt-2"><button type="button" className="text-xs text-brand-secondary" onClick={() => void open()}>{shown ? "Ocultar fotos" : "Ver fotos"}</button>
        {error && <p className="text-xs text-error-primary">{error}</p>}
        {canEdit && <div className="mt-2 flex flex-wrap items-center gap-2"><input type="file" accept="image/jpeg,image/png,image/webp" className="text-xs text-secondary" onChange={(event) => setUpload(event.target.files?.[0] ?? null)} /><button type="button" className="text-xs text-brand-secondary disabled:opacity-40" disabled={!upload} onClick={() => void addPhoto()}>Adicionar foto</button></div>}
        {shown && (images.length ? <div className="mt-2 flex flex-wrap gap-2">{images.map(({ name, url }) => <a key={url} href={url} target="_blank" rel="noreferrer"><img src={url} alt={name} className="h-24 w-24 rounded-lg border border-secondary object-cover" /></a>)}</div> : <p className="text-xs text-tertiary">Sem fotos neste registro.</p>)}
    </div>;
}

export function PropertySeasonNotebook({ property }: { property: SavedProperty }) {
    const canEdit = property.accessRole !== "VIEWER";
    const [plots, setPlots] = useState<Plot[]>([]);
    const [seasons, setSeasons] = useState<PlotSeason[]>([]);
    const [activities, setActivities] = useState<PropertyActivity[]>([]);
    const [assignees, setAssignees] = useState<TeamAssignee[]>([]);
    const [fieldSettings, setFieldSettings] = useState<FieldSettings | null>(null);
    const [plotId, setPlotId] = useState("");
    const [crop, setCrop] = useState("");
    const [variety, setVariety] = useState("");
    const [plantingDate, setPlantingDate] = useState(today());
    const [closeDate, setCloseDate] = useState(today());
    const [operationPlotId, setOperationPlotId] = useState("");
    const [seasonId, setSeasonId] = useState("");
    const [type, setType] = useState<PropertyActivityType>("INSPECTION");
    const [date, setDate] = useState(today());
    const [notes, setNotes] = useState("");
    const [responsibleId, setResponsibleId] = useState("");
    const [inputName, setInputName] = useState("");
    const [inputQuantity, setInputQuantity] = useState("");
    const [inputUnit, setInputUnit] = useState("");
    const [harvestQuantity, setHarvestQuantity] = useState("");
    const [harvestUnit, setHarvestUnit] = useState("kg");
    const [photo, setPhoto] = useState<File | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [photoWarning, setPhotoWarning] = useState("");

    const reload = useCallback(async () => {
        const [nextPlots, nextSeasons, nextActivities, nextAssignees] = await Promise.all([
            listPlots(property.propertyId), listPlotSeasons(property.propertyId),
            listPropertyActivities(property.propertyId), listTeamAssignees(property.propertyId),
        ]);
        setPlots(nextPlots); setSeasons(nextSeasons); setActivities(nextActivities); setAssignees(nextAssignees);
        setPlotId((value) => nextPlots.some((plot) => plot.plotId === value) ? value : nextPlots[0]?.plotId ?? "");
        setOperationPlotId((value) => nextPlots.some((plot) => plot.plotId === value) ? value : nextPlots[0]?.plotId ?? "");
        setResponsibleId((value) => nextAssignees.some((member) => member.userId === value) ? value : nextAssignees[0]?.userId ?? "");
    }, [property.propertyId]);

    useEffect(() => { void reload().catch((reason: unknown) => setError(propertyErrorMessage(reason, "Não foi possível carregar safras e registros."))); }, [reload]);
    useEffect(() => {
        const controller = new AbortController();
        setFieldSettings(null);
        loadFieldSettings(property.propertyId, controller.signal).then((result) => {
            if (controller.signal.aborted) return;
            setFieldSettings(result);
            setHarvestUnit(result.defaultHarvestUnit);
        }).catch((reason: unknown) => {
            if (!controller.signal.aborted) setError(propertyErrorMessage(reason, "Não foi possível carregar as configurações de campo."));
        });
        return () => controller.abort();
    }, [property.propertyId]);
    useEffect(() => {
        const refresh = (event: Event) => {
            if ((event as CustomEvent<string>).detail === property.propertyId)
                void reload().catch((reason: unknown) => setError(propertyErrorMessage(reason, "Não foi possível atualizar safras e registros.")));
        };
        window.addEventListener("sentinela:property-activities-changed", refresh);
        return () => window.removeEventListener("sentinela:property-activities-changed", refresh);
    }, [property.propertyId, reload]);
    useEffect(() => { if (!seasons.some((season) => season.seasonId === seasonId && season.plotId === operationPlotId)) setSeasonId(""); }, [operationPlotId, seasonId, seasons]);
    const run = async (task: () => Promise<void>) => {
        if (busy) return;
        setBusy(true); setError("");
        try { await task(); await reload(); window.dispatchEvent(new CustomEvent("sentinela:property-activities-changed", { detail: property.propertyId })); }
        catch (reason) { setError(propertyErrorMessage(reason, "Não foi possível salvar.")); }
        finally { setBusy(false); }
    };

    const startSeason = (event: FormEvent) => {
        event.preventDefault();
        if (!plotId || !crop.trim() || !plantingDate) return;
        void run(async () => { await createPlotSeason(property.propertyId, plotId, { cropName: crop.trim(), variety: variety.trim() || null, plantingDate }); setCrop(""); setVariety(""); });
    };
    const saveOperation = (event: FormEvent) => {
        event.preventDefault();
        if (!operationPlotId || !notes.trim() || !responsibleId) return;
        if (fieldSettings?.requireSeasonForFieldRecords &&
                ["PLANTING", "FERTILIZATION", "SPRAYING", "HARVEST"].includes(type) && !seasonId) {
            setError("Selecione uma safra para esta operação.");
            return;
        }
        void run(async () => {
            const record = await createPropertyActivity(property.propertyId, {
                type, date, time: null, notes: notes.trim(), amountMm: null, plotId: operationPlotId,
                seasonId: seasonId || null, responsibleUserId: responsibleId,
                inputName: inputName.trim() || null, inputQuantity: inputQuantity ? Number(inputQuantity) : null,
                inputUnit: inputUnit.trim() || null, harvestQuantity: harvestQuantity ? Number(harvestQuantity) : null,
                harvestUnit: harvestQuantity ? harvestUnit.trim() : null,
            });
            setPhotoWarning("");
            if (photo) {
                try { await uploadActivityPhoto(property.propertyId, record.activityId, photo); }
                catch (reason) { setPhotoWarning(`Registro salvo, mas a foto falhou: ${propertyErrorMessage(reason, "tente enviar novamente.")}`); }
            }
            setNotes(""); setInputName(""); setInputQuantity(""); setInputUnit(""); setHarvestQuantity(""); setPhoto(null);
        });
    };
    const relevant = activities.filter((row) => row.plotId && (row.seasonId || row.inputName || row.harvestQuantity || row.responsibleUserId));

    return <section className="grid gap-4 lg:col-span-2" aria-label="Safras e caderno de campo">
        {error && <FormAlert>{error}</FormAlert>}{photoWarning && <FormAlert>{photoWarning}</FormAlert>}
        <div className="grid gap-4 lg:grid-cols-2">
            <div className="rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5">
                <h2 className="text-lg font-semibold text-primary">Safras por talhão</h2>
                <p className="mt-1 text-sm text-tertiary">Abra e encerre ciclos sem perder o histórico de culturas.</p>
                {canEdit && <form onSubmit={startSeason} className="mt-4 grid gap-3 sm:grid-cols-2">
                    <label className="text-xs text-secondary">Talhão<select className={field} value={plotId} onChange={(e) => setPlotId(e.target.value)} required>{plots.map((plot) => <option key={plot.plotId} value={plot.plotId}>{plot.name}</option>)}</select></label>
                    <label className="text-xs text-secondary">Cultura<input className={field} value={crop} onChange={(e) => setCrop(e.target.value)} required maxLength={120} /></label>
                    <label className="text-xs text-secondary">Variedade<input className={field} value={variety} onChange={(e) => setVariety(e.target.value)} maxLength={120} /></label>
                    <label className="text-xs text-secondary">Data do plantio<input className={field} type="date" max={today()} value={plantingDate} onChange={(e) => setPlantingDate(e.target.value)} required /></label>
                    <div><Button type="submit" size="sm" isLoading={busy} isDisabled={!plots.length}>Iniciar safra</Button></div>
                </form>}
                <ul className="mt-4 divide-y divide-secondary">{seasons.map((season) => <li key={season.seasonId} className="py-3 text-sm">
                    <p className="font-semibold text-primary">{season.cropName}{season.variety ? ` · ${season.variety}` : ""} <span className="font-normal text-tertiary">· {plots.find((plot) => plot.plotId === season.plotId)?.name ?? "Talhão removido"}</span></p>
                    <p className="text-tertiary">Plantio {season.plantingDate} · {season.status === "ACTIVE" ? "Em andamento" : `Colheita ${season.harvestDate}`}</p>
                    {canEdit && season.status === "ACTIVE" && <div className="mt-2 flex flex-wrap items-end gap-2"><label className="text-xs text-secondary">Data de encerramento<input className={field} type="date" min={season.plantingDate} max={today()} value={closeDate} onChange={(e) => setCloseDate(e.target.value)} /></label><Button type="button" size="sm" color="secondary" isLoading={busy} onClick={() => void run(async () => { await closePlotSeason(property.propertyId, season.plotId, season.seasonId, closeDate); })}>Encerrar</Button></div>}
                </li>)}</ul>
                {seasons.length === 0 && <p className="mt-4 text-sm text-tertiary">Nenhuma safra cadastrada.</p>}
            </div>
            <div className="rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5">
                <h2 className="text-lg font-semibold text-primary">Caderno de campo</h2>
                <p className="mt-1 text-sm text-tertiary">Registre operações, insumos, responsável, colheita e evidência fotográfica.</p>
                {canEdit && <form onSubmit={saveOperation} className="mt-4 grid gap-3 sm:grid-cols-2">
                    <label className="text-xs text-secondary">Talhão<select className={field} required value={operationPlotId} onChange={(e) => setOperationPlotId(e.target.value)}>{plots.map((plot) => <option key={plot.plotId} value={plot.plotId}>{plot.name}</option>)}</select></label>
                    <label className="text-xs text-secondary">Safra<select className={field} value={seasonId} required={Boolean(fieldSettings?.requireSeasonForFieldRecords && ["PLANTING", "FERTILIZATION", "SPRAYING", "HARVEST"].includes(type))} onChange={(e) => setSeasonId(e.target.value)}><option value="">Sem safra vinculada</option>{seasons.filter((season) => season.plotId === operationPlotId).map((season) => <option key={season.seasonId} value={season.seasonId}>{season.cropName} · {season.plantingDate}</option>)}</select></label>
                    <label className="text-xs text-secondary">Operação<select className={field} value={type} onChange={(e) => setType(e.target.value as PropertyActivityType)}>{(Object.keys(labels) as PropertyActivityType[]).filter((key) => key !== "RAINFALL").map((key) => <option key={key} value={key}>{labels[key]}</option>)}</select></label>
                    <label className="text-xs text-secondary">Data<input className={field} type="date" max={today()} required value={date} onChange={(e) => setDate(e.target.value)} /></label>
                    <label className="text-xs text-secondary">Responsável<select className={field} value={responsibleId} onChange={(e) => setResponsibleId(e.target.value)} required>{assignees.map((member) => <option key={member.userId} value={member.userId}>{member.name}</option>)}</select></label>
                    <label className="text-xs text-secondary">Insumo<input className={field} maxLength={120} value={inputName} onChange={(e) => setInputName(e.target.value)} placeholder="Opcional" /></label>
                    <label className="text-xs text-secondary">Quantidade do insumo<input className={field} type="number" min="0.001" step="0.001" value={inputQuantity} onChange={(e) => setInputQuantity(e.target.value)} disabled={!inputName.trim()} /></label>
                    <label className="text-xs text-secondary">Unidade do insumo<input className={field} maxLength={24} value={inputUnit} onChange={(e) => setInputUnit(e.target.value)} placeholder="kg, L, saco..." disabled={!inputName.trim()} /></label>
                    {type === "HARVEST" && <><label className="text-xs text-secondary">Quantidade colhida<input className={field} type="number" min="0.001" step="0.001" value={harvestQuantity} onChange={(e) => setHarvestQuantity(e.target.value)} /></label><label className="text-xs text-secondary">Unidade da colheita<input className={field} maxLength={24} value={harvestUnit} onChange={(e) => setHarvestUnit(e.target.value)} /></label></>}
                    <label className="text-xs text-secondary sm:col-span-2">Descrição<textarea className={field} required maxLength={1000} rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} /></label>
                    <label className="text-xs text-secondary sm:col-span-2">Foto (JPEG, PNG ou WebP, até 5 MB)<input className={field} type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => setPhoto(e.target.files?.[0] ?? null)} /></label>
                    <div><Button type="submit" size="sm" isLoading={busy} isDisabled={!plots.length}>Salvar operação</Button></div>
                </form>}
            </div>
        </div>
        <div className="rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5">
            <h3 className="text-sm font-semibold text-primary">Histórico de operações</h3>
            {relevant.length === 0 ? <p className="mt-3 text-sm text-tertiary">Nenhuma operação estruturada registrada.</p> : <ul className="mt-2 divide-y divide-secondary">{relevant.map((row) => <li key={row.activityId} className="py-3 text-sm">
                <p className="font-semibold text-primary">{labels[row.type]} · {row.date} · {plots.find((plot) => plot.plotId === row.plotId)?.name ?? "Talhão removido"}</p>
                <p className="text-secondary">{row.notes}</p>
                <p className="text-xs text-tertiary">{row.responsibleName && `Responsável: ${row.responsibleName} · `}{row.inputName && `${row.inputName}: ${row.inputQuantity} ${row.inputUnit} · `}{row.harvestQuantity && `Colheita: ${row.harvestQuantity} ${row.harvestUnit}`}</p>
                <Photos propertyId={property.propertyId} activityId={row.activityId} canEdit={canEdit} />
            </li>)}</ul>}
        </div>
    </section>;
}
