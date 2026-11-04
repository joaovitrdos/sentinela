import { FormAlert } from "@/components/app/form-alert";
import { ConfirmationModal } from "@/components/app/confirmation-modal";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Check, Edit03, Map01, Plus } from "@untitledui/icons";
import { ApiFilterMenu } from "@/components/app/hydro/api-filter-menu";
import { Legend } from "@/components/app/hydro/map-controls";
import { PropertyMap } from "@/components/app/properties/property-map";
import { PropertyMonitoring } from "@/components/app/properties/property-monitoring";
import { PropertyFarmPanel } from "@/components/app/properties/property-farm-panel";
import { Button } from "@/components/base/buttons/button";
import { approximateHectares, closedPropertyGeometry, listProperties, openPropertyRing, propertyErrorMessage, saveProperty } from "@/lib/property-data";
import type { PropertyCoordinate, PropertyImageOverlay, SavedProperty } from "@/lib/property-data";

const fieldClass =
    "w-full rounded-lg border border-primary bg-primary px-3 py-2 text-sm text-primary outline-brand focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-50";
const formatArea = (value: number | null | undefined) =>
    value != null && Number.isFinite(value) ? `${value.toLocaleString("pt-BR", { maximumFractionDigits: 2 })} ha` : "Área não delimitada";
const localToday = () => {
    const date = new Date();
    date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
    return date.toISOString().slice(0, 10);
};
const emptyForm = { name: "", city: "", state: "", cropName: "", plotName: "", plantingDate: "", cropStage: "" };

export const PropertiesPage = () => {
    const [properties, setProperties] = useState<SavedProperty[]>([]);
    const [visibleIds, setVisibleIds] = useState<string[]>([]);
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [filtersOpen, setFiltersOpen] = useState(false);
    const [infoOpen, setInfoOpen] = useState(false);
    const [loading, setLoading] = useState(true);
    const [listError, setListError] = useState<string | null>(null);
    const [formError, setFormError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    const [editing, setEditing] = useState(false);
    const [drawing, setDrawing] = useState(false);
    const [closed, setClosed] = useState(false);
    const [vertices, setVertices] = useState<PropertyCoordinate[]>([]);
    const [form, setForm] = useState(emptyForm);
    const [busy, setBusy] = useState(false);
    const [pendingDiscard, setPendingDiscard] = useState<{ description: string; action: () => void } | null>(null);
    const [overlay, setOverlay] = useState<PropertyImageOverlay | null>(null);
    const [inspectionSuggestion, setInspectionSuggestion] = useState<{ propertyId: string; note: string; key: number } | null>(null);
    const [reload, setReload] = useState(0);
    const returnId = useRef<string | null>(null);
    const selected = useMemo(() => properties.find((property) => property.propertyId === selectedId) ?? null, [properties, selectedId]);
    const visibleProperties = useMemo(() => properties.filter((property) => visibleIds.includes(property.propertyId)), [properties, visibleIds]);
    const filterOptions = useMemo(
        () => properties.map((property) => ({ id: property.propertyId, label: `${property.name} · ${property.city} / ${property.state}`, icon: Map01 })),
        [properties],
    );
    const area = useMemo(() => approximateHectares(vertices), [vertices]);

    useEffect(() => {
        const controller = new AbortController();
        setLoading(true);
        setListError(null);
        listProperties(controller.signal)
            .then((result) => {
                if (controller.signal.aborted) return;
                setProperties(result);
                setVisibleIds(result.map((property) => property.propertyId));
                setSelectedId(null);
                setVertices([]);
                setClosed(false);
                setOverlay(null);
                setInfoOpen(!result.length);
            })
            .catch((reason: unknown) => {
                if (!controller.signal.aborted) {
                    setListError(propertyErrorMessage(reason, "Não foi possível carregar suas propriedades."));
                    setInfoOpen(true);
                }
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [reload]);

    const loadIntoForm = (property: SavedProperty | null) => {
        setSelectedId(property?.propertyId ?? null);
        setForm(property ? {
            name: property.name, city: property.city, state: property.state,
            cropName: property.cropName ?? "", plotName: property.plotName ?? "",
            plantingDate: property.plantingDate ?? "", cropStage: property.cropStage ?? "",
        } : emptyForm);
        setVertices(openPropertyRing(property?.geometry ?? null));
        setClosed(!!property?.geometry);
        setFormError(null);
        setOverlay(null);
    };

    const afterDiscardConfirmation = (description: string, action: () => void) => {
        if (editing) { setPendingDiscard({ description, action }); return; }
        action();
    };

    const selectProperty = (property: SavedProperty) => {
        if (busy || !visibleIds.includes(property.propertyId)) return;
        if (!editing && selectedId === property.propertyId) {
            setInfoOpen(true);
            return;
        }
        afterDiscardConfirmation("Descartar as alterações não salvas desta propriedade?", () => {
            loadIntoForm(property);
            setEditing(false);
            setDrawing(false);
            setSuccess(null);
            setInfoOpen(true);
        });
    };

    const create = () => {
        if (busy || loading) return;
        afterDiscardConfirmation("Descartar as alterações não salvas e cadastrar outra propriedade?", () => {
            if (!editing) returnId.current = selectedId;
            loadIntoForm(null);
            setEditing(true);
            setDrawing(true);
            setSuccess(null);
            setInfoOpen(true);
            setFiltersOpen(false);
        });
    };

    const edit = () => {
        if (!selected || busy || selected.accessRole === "VIEWER") return;
        returnId.current = selectedId;
        setEditing(true);
        setDrawing(!selected?.geometry);
        setFormError(null);
        setSuccess(null);
        setOverlay(null);
        setInfoOpen(true);
        setFiltersOpen(false);
    };

    const cancel = () => {
        loadIntoForm(properties.find((property) => property.propertyId === returnId.current && visibleIds.includes(property.propertyId)) ?? null);
        setEditing(false);
        setDrawing(false);
        setSuccess(null);
    };

    const applyVisibleIds = (ids: string[]) => {
        if (busy || loading) return;
        afterDiscardConfirmation("Descartar as alterações não salvas e mudar as propriedades visíveis?", () => {
            setVisibleIds(ids);
            const onlyProperty = ids.length === 1 ? (properties.find((property) => property.propertyId === ids[0]) ?? null) : null;
            loadIntoForm(onlyProperty);
            setEditing(false);
            setDrawing(false);
            setSuccess(null);
            setInfoOpen(!!onlyProperty);
            returnId.current = null;
        });
    };

    const toggleProperty = (id: string) => applyVisibleIds(visibleIds.includes(id) ? visibleIds.filter((value) => value !== id) : [...visibleIds, id]);
    const toggleAll = () => applyVisibleIds(visibleIds.length === properties.length ? [] : properties.map((property) => property.propertyId));

    const finish = useCallback(() => {
        if (vertices.length >= 3) {
            setClosed(true);
            setDrawing(false);
        }
    }, [vertices.length]);

    const citySelected = useCallback(
        (city: string, state: string) => {
            if (editing) setForm((current) => ({ ...current, city, state }));
        },
        [editing],
    );

    const save = async (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setFormError(null);
        setSuccess(null);
        if (!closed || vertices.length < 3) {
            setFormError("Marque pelo menos três pontos e conclua o contorno da propriedade antes de salvar.");
            return;
        }
        if (!form.name.trim() || !form.city.trim() || !form.state.trim()) {
            setFormError("Preencha o nome, a cidade e o estado da propriedade.");
            return;
        }
        setBusy(true);
        try {
            const saved = await saveProperty(
                {
                    name: form.name.trim(), city: form.city.trim(), state: form.state.trim(), geometry: closedPropertyGeometry(vertices),
                    cropName: form.cropName.trim(), plotName: form.plotName.trim(), plantingDate: form.plantingDate || null,
                    cropStage: form.cropStage,
                },
                selectedId ?? undefined,
            );
            setProperties((current) => [saved, ...current.filter((property) => property.propertyId !== saved.propertyId)]);
            setVisibleIds((current) => (current.includes(saved.propertyId) ? current : [...current, saved.propertyId]));
            loadIntoForm(saved);
            setEditing(false);
            setDrawing(false);
            setSuccess("Propriedade salva. O monitoramento utiliza somente o contorno cadastrado.");
            returnId.current = null;
        } catch (reason) {
            setFormError(propertyErrorMessage(reason, "Não foi possível salvar a propriedade. Revise os dados e o contorno."));
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="flex min-h-[calc(100dvh-4rem)] w-full flex-col gap-3 px-[15px] py-3">
            <h1 className="sr-only">Propriedades</h1>
            <section className="flex min-w-0 flex-1 flex-col" aria-label="Mapa das suas propriedades">
                <PropertyMap
                    vertices={vertices}
                    closed={closed}
                    editable={editing && !busy}
                    drawing={drawing}
                    fitKey={`${visibleIds.join(",")}:${selectedId ?? "all"}:${editing ? "editing" : "view"}`}
                    overlay={editing ? null : overlay}
                    visibleProperties={visibleProperties}
                    onVerticesChange={setVertices}
                    onFinish={finish}
                    onCitySelect={citySelected}
                    onPropertySelect={(id) => {
                        const property = properties.find((item) => item.propertyId === id);
                        if (property) selectProperty(property);
                    }}
                    toolbarExtra={
                        <>
                            <ApiFilterMenu<string>
                                open={filtersOpen}
                                onOpenChange={setFiltersOpen}
                                title="Filtrar propriedades"
                                selectAllLabel="Todas as propriedades"
                                disabled={busy || loading}
                                options={filterOptions}
                                selected={visibleIds}
                                onToggle={toggleProperty}
                                onSelectAll={toggleAll}
                                hint="Escolha as áreas visíveis. Clique em uma propriedade no mapa para ver seus dados e monitoramento."
                            />
                            <Button color="primary" size="sm" iconLeading={Plus} onClick={create} isDisabled={busy || loading}>
                                Nova propriedade
                            </Button>
                        </>
                    }
                    overlayPanel={
                        <Legend
                            inline
                            scrollable={false}
                            title={
                                editing
                                    ? selectedId
                                        ? "Editar propriedade"
                                        : "Cadastrar propriedade"
                                    : (selected?.name ??
                                      (loading
                                          ? "Carregando propriedades…"
                                          : listError
                                            ? "Não foi possível carregar"
                                            : `${visibleIds.length} ${visibleIds.length === 1 ? "propriedade visível" : "propriedades visíveis"}`))
                            }
                            headerLabel={editing ? "Cadastro" : "Informações"}
                            open={infoOpen}
                            onOpenChange={setInfoOpen}
                            className="bg-primary/95 shadow-lg ring-secondary_alt"
                            footer={
                                editing || selected
                                    ? "O contorno e a área são aproximados. As imagens do mapa base podem ter datas diferentes das imagens Sentinel-2."
                                    : undefined
                            }
                        >
                            <div className="p-3">
                                <FormAlert type="success">{success}</FormAlert>
                                {loading && (
                                    <p role="status" className="text-sm text-tertiary">
                                        Carregando suas propriedades…
                                    </p>
                                )}
                                {listError && (
                                    <div>
                                        <FormAlert>
                                            {listError}
                                        </FormAlert>
                                        <Button color="secondary" size="sm" className="mt-3" onClick={() => setReload((value) => value + 1)}>
                                            Tentar novamente
                                        </Button>
                                    </div>
                                )}
                                {!loading && !listError && !properties.length && !editing && (
                                    <div className="rounded-xl border border-dashed border-secondary p-3">
                                        <Map01 className="size-6 text-tertiary" aria-hidden="true" />
                                        <p className="mt-3 text-sm font-medium text-primary">Sua primeira área começa aqui</p>
                                        <p className="mt-1 text-xs leading-5 text-tertiary">
                                            Cadastre uma propriedade e marque seus limites no mapa. Apenas suas propriedades aparecem nesta tela.
                                        </p>
                                    </div>
                                )}
                                {!loading && !listError && !!properties.length && !selected && !editing && (
                                    <p className="text-xs leading-5 text-tertiary">
                                        {visibleIds.length
                                            ? "Clique em uma área no mapa para ver os detalhes. Use o filtro para selecionar uma propriedade ou todas."
                                            : "Nenhuma propriedade selecionada. Abra o filtro e escolha as áreas que deseja visualizar."}
                                    </p>
                                )}
                                {(editing || selected) && (
                                    <section aria-label={editing ? "Cadastro da propriedade" : "Área selecionada"}>
                                        <div className="flex items-center justify-between gap-2">
                                            <h2 className="text-sm font-semibold text-primary">
                                                {editing ? (selectedId ? "Editar propriedade" : "Cadastrar propriedade") : "Área selecionada"}
                                            </h2>
                                            {!editing && selected && selected.accessRole !== "VIEWER" && (
                                                <Button
                                                    color="tertiary"
                                                    size="xs"
                                                    iconLeading={Edit03}
                                                    onClick={edit}
                                                    aria-label="Editar propriedade"
                                                    isDisabled={busy}
                                                >
                                                    Editar
                                                </Button>
                                            )}
                                        </div>
                                        {editing ? (
                                            <form className="mt-4 space-y-3.5" onSubmit={(event) => void save(event)}>
                                                <label className="flex flex-col gap-1.5 text-xs font-medium text-secondary">
                                                    Nome da propriedade
                                                    <input
                                                        required
                                                        maxLength={120}
                                                        className={fieldClass}
                                                        value={form.name}
                                                        onChange={(event) => setForm({ ...form, name: event.target.value })}
                                                        placeholder="Ex.: Fazenda Boa Vista"
                                                        disabled={busy}
                                                    />
                                                </label>
                                                <label className="flex flex-col gap-1.5 text-xs font-medium text-secondary">
                                                    Cidade
                                                    <input
                                                        required
                                                        maxLength={120}
                                                        className={fieldClass}
                                                        value={form.city}
                                                        onChange={(event) => setForm({ ...form, city: event.target.value })}
                                                        placeholder="Busque no mapa ou informe a cidade"
                                                        disabled={busy}
                                                    />
                                                </label>
                                                <label className="flex flex-col gap-1.5 text-xs font-medium text-secondary">
                                                    Estado / UF
                                                    <input
                                                        required
                                                        maxLength={80}
                                                        className={fieldClass}
                                                        value={form.state}
                                                        onChange={(event) => setForm({ ...form, state: event.target.value })}
                                                        placeholder="Ex.: ES"
                                                        disabled={busy}
                                                    />
                                                </label>
                                                <div className="grid grid-cols-2 gap-3">
                                                    <label className="flex flex-col gap-1.5 text-xs font-medium text-secondary">
                                                        Cultura
                                                        <input maxLength={120} className={fieldClass} value={form.cropName}
                                                            onChange={(event) => setForm({ ...form, cropName: event.target.value })}
                                                            placeholder="Ex.: soja" disabled={busy} />
                                                    </label>
                                                    <label className="flex flex-col gap-1.5 text-xs font-medium text-secondary">
                                                        Talhão
                                                        <input maxLength={120} className={fieldClass} value={form.plotName}
                                                            onChange={(event) => setForm({ ...form, plotName: event.target.value })}
                                                            placeholder="Ex.: Talhão 1" disabled={busy} />
                                                    </label>
                                                </div>
                                                <div className="grid grid-cols-2 gap-3">
                                                    <label className="flex flex-col gap-1.5 text-xs font-medium text-secondary">
                                                        Data de plantio
                                                        <input type="date" max={localToday()} className={fieldClass} value={form.plantingDate}
                                                            onChange={(event) => setForm({ ...form, plantingDate: event.target.value })} disabled={busy} />
                                                    </label>
                                                    <label className="flex flex-col gap-1.5 text-xs font-medium text-secondary">
                                                        Fase da lavoura
                                                        <select className={fieldClass} value={form.cropStage}
                                                            onChange={(event) => setForm({ ...form, cropStage: event.target.value })} disabled={busy}>
                                                            <option value="">Selecione</option>
                                                            <option value="Implantação">Implantação</option>
                                                            <option value="Desenvolvimento vegetativo">Desenvolvimento vegetativo</option>
                                                            <option value="Florescimento">Florescimento</option>
                                                            <option value="Frutificação / enchimento">Frutificação / enchimento</option>
                                                            <option value="Maturação">Maturação</option>
                                                            <option value="Pós-colheita">Pós-colheita</option>
                                                        </select>
                                                    </label>
                                                </div>
                                                <div className="rounded-xl border border-secondary p-3">
                                                    <div className="flex items-center justify-between gap-2 text-sm">
                                                        <span className="font-medium text-secondary">Contorno</span>
                                                        <span className="text-primary tabular-nums">{formatArea(area)}</span>
                                                    </div>
                                                    <p className="mt-1.5 text-xs leading-5 text-tertiary">
                                                        {closed
                                                            ? "Área concluída. Arraste os vértices no mapa para ajustar."
                                                            : "Use a busca ou sua localização, aproxime o mapa e clique nos limites da área."}
                                                    </p>
                                                    <div className="mt-3 flex flex-wrap gap-2">
                                                        {drawing && (
                                                            <Button
                                                                size="xs"
                                                                color="secondary"
                                                                isDisabled={!vertices.length || busy}
                                                                onClick={() => setVertices((current) => current.slice(0, -1))}
                                                            >
                                                                Desfazer ponto
                                                            </Button>
                                                        )}
                                                        <Button
                                                            size="xs"
                                                            color="secondary"
                                                            isDisabled={!vertices.length || busy}
                                                            onClick={() => {
                                                                setVertices([]);
                                                                setClosed(false);
                                                                setDrawing(true);
                                                            }}
                                                        >
                                                            Redesenhar
                                                        </Button>
                                                        {drawing && (
                                                            <Button
                                                                size="xs"
                                                                color="secondary"
                                                                iconLeading={Check}
                                                                isDisabled={vertices.length < 3 || busy}
                                                                onClick={finish}
                                                            >
                                                                Concluir
                                                            </Button>
                                                        )}
                                                    </div>
                                                </div>
                                                {formError && (
                                                    <FormAlert>
                                                        {formError}
                                                    </FormAlert>
                                                )}
                                                <div className="flex gap-2">
                                                    <Button type="submit" size="sm" color="primary" isLoading={busy} className="flex-1">
                                                        Salvar propriedade
                                                    </Button>
                                                    <Button size="sm" color="secondary" onClick={cancel} isDisabled={busy}>
                                                        Cancelar
                                                    </Button>
                                                </div>
                                            </form>
                                        ) : (
                                            selected && (
                                                <>
                                                    <dl className="mt-4 space-y-3 text-sm">
                                                        <div>
                                                            <dt className="text-xs text-tertiary">Nome</dt>
                                                            <dd className="mt-1 font-medium break-words text-primary">{selected.name}</dd>
                                                        </div>
                                                        <div>
                                                            <dt className="text-xs text-tertiary">Local</dt>
                                                            <dd className="mt-1 text-secondary">
                                                                {selected.city} · {selected.state}
                                                            </dd>
                                                        </div>
                                                        <div>
                                                            <dt className="text-xs text-tertiary">Área delimitada</dt>
                                                            <dd className="mt-1 text-secondary tabular-nums">{formatArea(selected.areaHectares)}</dd>
                                                        </div>
                                                        <div>
                                                            <dt className="text-xs text-tertiary">Cultura / talhão</dt>
                                                            <dd className="mt-1 text-secondary">{[selected.cropName, selected.plotName].filter(Boolean).join(" · ") || "Não informado"}</dd>
                                                        </div>
                                                        <div>
                                                            <dt className="text-xs text-tertiary">Plantio / fase</dt>
                                                            <dd className="mt-1 text-secondary">{[selected.plantingDate, selected.cropStage].filter(Boolean).join(" · ") || "Não informado"}</dd>
                                                        </div>
                                                    </dl>
                                                    {formError && (
                                                        <FormAlert>
                                                            {formError}
                                                        </FormAlert>
                                                    )}
                                                </>
                                            )
                                        )}
                                    </section>
                                )}
                            </div>
                        </Legend>
                    }
                />
            </section>
            <PropertyMonitoring property={editing ? null : selected} onOverlay={setOverlay} onPlanInspection={(note) => { if (selected) setInspectionSuggestion({ propertyId: selected.propertyId, note, key: Date.now() }); }} />
            <PropertyFarmPanel property={editing ? null : selected} inspectionSuggestion={inspectionSuggestion} />
            <ConfirmationModal open={pendingDiscard !== null} title="Descartar alterações?"
                description={pendingDiscard?.description ?? ""} confirmLabel="Descartar" cancelLabel="Continuar editando"
                destructive onConfirm={() => { const action = pendingDiscard?.action; setPendingDiscard(null); action?.(); }}
                onCancel={() => setPendingDiscard(null)} />
        </div>
    );
};

export default PropertiesPage;
