import { useState } from "react";
import { useTranslation } from "react-i18next";
import { FormAlert } from "@/components/app/form-alert";
import { Button } from "@/components/base/buttons/button";
import type { CarProperty } from "@/lib/advisory";
import { advisoryApi } from "@/lib/advisory";
import { propertyErrorMessage } from "@/lib/property-data";

const field = "w-full min-w-0 rounded-lg border border-primary bg-primary px-3 py-2 text-sm text-primary outline-brand focus-visible:outline-2";

/** Fica dentro do formulário da propriedade: nunca envia o formulário, só preenche o contorno e a cidade. */
export const PropertyCarImport = ({ onImport, disabled }: { onImport: (property: CarProperty) => void; disabled?: boolean }) => {
    const { t } = useTranslation();
    const [code, setCode] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [result, setResult] = useState("");

    const search = async () => {
        if (!code.trim()) return;
        setBusy(true);
        setError("");
        setResult("");
        try {
            const found = await advisoryApi.car(code.trim());
            onImport(found);
            const notes = [t("advisor.car.found", { city: found.city, state: found.state })];
            if (found.simplified) notes.push(t("advisor.car.simplified"));
            if (found.ignoredParts > 0) notes.push(t("advisor.car.parts", { count: found.ignoredParts }));
            setResult(notes.join(" "));
        } catch (reason) {
            setError(propertyErrorMessage(reason, t("advisor.car.error")));
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="rounded-lg border border-secondary p-3">
            <label className="flex flex-col gap-1.5 text-xs font-medium text-secondary">
                {t("advisor.car.label")}
                <span className="flex gap-2">
                    <input
                        className={field}
                        value={code}
                        placeholder="GO-5207808-0530.A0B5.7A18.4968.987F.8FE2.DBA3.13ED"
                        disabled={disabled || busy}
                        onChange={(event) => setCode(event.target.value)}
                        onKeyDown={(event) => {
                            if (event.key !== "Enter") return;
                            event.preventDefault();
                            void search();
                        }}
                    />
                    <Button type="button" size="sm" color="secondary" onClick={() => void search()} isLoading={busy} isDisabled={disabled || !code.trim()}>
                        {t("advisor.car.search")}
                    </Button>
                </span>
            </label>
            <p className="mt-1.5 text-xs text-tertiary">{result || t("advisor.car.hint")}</p>
            {error ? <div className="mt-2"><FormAlert>{error}</FormAlert></div> : null}
        </div>
    );
};
