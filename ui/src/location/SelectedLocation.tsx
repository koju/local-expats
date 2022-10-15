import {createContext, FC, PropsWithChildren, useContext, useState} from "react";
import {LocationDto} from "./LocationDto";
import {get} from "../util/Fetch";
import {useQuery} from "@tanstack/react-query";
import {Loading} from "../util/Loading";

export type LocationId = number;
type SetSelectedLocation = (selectedLocation: LocationDto) => void;
type SetSelectedLocationId = (selectedLocationId: LocationId) => void;

type UseSelectedLocation = {
    locations: readonly LocationDto[]
    selectedLocation: LocationDto;
    setSelectedLocation: SetSelectedLocation;
    setSelectedLocationId: SetSelectedLocationId;
    getLocation: (locationId: LocationId) => LocationDto;
}

const SelectedLocationContext = createContext<UseSelectedLocation | undefined>(undefined);

export const SelectedLocationProvider: FC<PropsWithChildren> = ({children}) => {
    const us = {id: 1};
    const [selectedLocation, setSelectedLocation] = useState<LocationDto>(us);
    const fetchLocations = () => get<LocationDto[]>('/api/locations');
    const {isLoading, data} = useQuery(['locations'], fetchLocations, {
        refetchOnWindowFocus: false,
        refetchOnMount: false,
        refetchOnReconnect: false,
        staleTime: Infinity,
    });

    if (isLoading) {
        return <Loading/>
    }

    const locations = data ?? [];
    const getLocation = (locationId: LocationId): LocationDto => {
        const location = locations.find(({id}) => id === locationId);
        if (!location) {
            throw new Error(`Unknown locationId: ${locationId}`)
        }
        return location
    };

    const setSelectedLocationId = (locationId: LocationId) => {
        setSelectedLocation(getLocation(locationId));
    }

    return (
        <SelectedLocationContext.Provider value={{locations, getLocation, selectedLocation, setSelectedLocation, setSelectedLocationId}}>
            {children}
        </SelectedLocationContext.Provider>
    );
}

export function useSelectedLocation(): UseSelectedLocation {
    const useSelectedLocationContext = useContext(SelectedLocationContext);
    if (useSelectedLocationContext === undefined) {
        throw new Error('useSelectedLocation must be used within a SelectedLocationProvider');
    }
    return useSelectedLocationContext;
}