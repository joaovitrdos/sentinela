import { useEffect, useMemo, useState } from "react";
import { SearchLg } from "@untitledui/icons";
import type { Key } from "react-aria-components";
import { useTranslation } from "react-i18next";
import { ComboBox } from "@/components/base/select/combobox";
import { SelectItem } from "@/components/base/select/select-item";
import { cx } from "@/utils/cx";
import type { SearchEntry } from "./geo-data";
import { loadSearchIndex, normalizeText } from "./geo-data";

const MAX_RESULTS = 30;

interface IndexedEntry {
    id: number;
    nome: string;
    uf: string;
    key: string;
}

export const CitySearch = ({ onSelect, className }: { onSelect: (id: number, uf: string) => void; className?: string }) => {
    const { t } = useTranslation();
    const [index, setIndex] = useState<IndexedEntry[] | null>(null);
    const [query, setQuery] = useState("");

    // O indice (5.570 cidades) e baixado pouco depois do mapa aparecer, ou na hora se a pessoa for direto na busca.
    const ensureIndex = () => {
        if (index) return;
        loadSearchIndex()
            .then((rows: SearchEntry[]) => setIndex(rows.map(([id, nome, uf]) => ({ id, nome, uf, key: normalizeText(nome) }))))
            .catch(() => undefined);
    };

    useEffect(() => {
        const timer = setTimeout(ensureIndex, 1500);
        return () => clearTimeout(timer);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const results = useMemo(() => {
        const q = normalizeText(query);
        if (!index || q.length < 2) return [];
        const starts: IndexedEntry[] = [];
        const contains: IndexedEntry[] = [];
        for (const entry of index) {
            if (entry.key.startsWith(q)) starts.push(entry);
            else if (entry.key.includes(q)) contains.push(entry);
            if (starts.length >= MAX_RESULTS) break;
        }
        return [...starts, ...contains].slice(0, MAX_RESULTS).map((e) => ({ id: String(e.id), label: e.nome, supportingText: e.uf }));
    }, [index, query]);

    const handleSelect = (key: Key | null) => {
        if (key == null || !index) return;
        const entry = index.find((e) => String(e.id) === String(key));
        if (!entry) return;
        onSelect(entry.id, entry.uf);
        setQuery("");
    };

    return (
        <div className={cx("pointer-events-auto rounded-lg bg-primary", className)} onFocus={ensureIndex} onPointerEnter={ensureIndex}>
            <ComboBox
                aria-label={t("map.searchPlaceholder")}
                placeholder={t("map.searchPlaceholder")}
                size="sm"
                shortcut={false}
                icon={SearchLg}
                menuTrigger="input"
                items={results}
                inputValue={query}
                onInputChange={setQuery}
                selectedKey={null}
                onSelectionChange={handleSelect}
                allowsEmptyCollection={false}
            >
                {(item) => (
                    <SelectItem id={item.id} label={item.label} supportingText={item.supportingText}>
                        {item.label}
                    </SelectItem>
                )}
            </ComboBox>
        </div>
    );
};
