import { memo, useDeferredValue, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { normalizeText } from "@/components/app/brazil-map/geo-data";
import { Select } from "@/components/base/select/select";

export interface DashboardCityItem {
    id: string;
    label: string;
    searchKey: string;
}

const MAX_RESULTS = 40;

/** Keep the city collection bounded; typing does not re-render the dashboard. */
export const DashboardCityFilter = memo(function DashboardCityFilter({
    items,
    selectedKey,
    onSelectionChange,
    isDisabled,
}: {
    items: DashboardCityItem[];
    selectedKey: string;
    onSelectionChange: (key: string) => void;
    isDisabled: boolean;
}) {
    const { t } = useTranslation();
    const allLabel = t("dashboard.filters.allCities");
    const selectedItem = useMemo(() => items.find((item) => item.id === selectedKey), [items, selectedKey]);
    const selectedLabel = selectedItem?.label ?? allLabel;
    const [query, setQuery] = useState(selectedLabel);
    const deferredQuery = useDeferredValue(query);
    const search = deferredQuery === selectedLabel ? "" : normalizeText(deferredQuery);

    useEffect(() => setQuery(selectedLabel), [selectedLabel]);

    const results = useMemo(() => {
        const matches: DashboardCityItem[] = [];
        for (const item of items) {
            if (!search || item.searchKey.includes(search)) matches.push(item);
            if (matches.length === MAX_RESULTS) break;
        }
        return matches;
    }, [search, items]);

    const options = useMemo(() => {
        const rows: Array<{ id: string; label: string }> =
            !search || normalizeText(allLabel).includes(search) ? [{ id: "all", label: allLabel }, ...results] : [...results];
        if (!search && selectedItem && !results.some((item) => item.id === selectedItem.id)) {
            if (results.length === MAX_RESULTS) rows.pop();
            rows.push(selectedItem);
        }
        return rows;
    }, [allLabel, results, selectedItem, search]);

    return (
        <Select.ComboBox
            size="sm"
            label={t("dashboard.filters.city")}
            placeholder={t("map.searchPlaceholder")}
            shortcut={false}
            items={options}
            selectedKey={query === selectedLabel ? selectedKey : null}
            inputValue={query}
            onInputChange={setQuery}
            onFocus={() => setQuery("")}
            onSelectionChange={(key) => {
                if (key != null) {
                    onSelectionChange(String(key));
                    setQuery(options.find((item) => item.id === String(key))?.label ?? allLabel);
                }
            }}
            onBlur={() => setQuery(selectedLabel)}
            isDisabled={isDisabled}
            hint={options.length ? t("dashboard.filters.citySearchHint", { count: MAX_RESULTS }) : t("map.searchEmpty")}
        >
            {(item) => <Select.Item id={item.id} label={item.label} />}
        </Select.ComboBox>
    );
});
