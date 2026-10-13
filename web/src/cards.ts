import type { Establishment, Landmark } from './protocol';

// Card catalog for display and client-side hints. Rules are enforced by the server;
// values mirror core's Establishment and Landmark enums.

export type CardColor = 'BLUE' | 'GREEN' | 'RED' | 'PURPLE';
export type CardIcon = 'WHEAT' | 'COW' | 'BREAD' | 'CUP' | 'GEAR' | 'FACTORY' | 'FRUIT' | 'TOWER';

export interface EstablishmentInfo {
  name: string;
  color: CardColor;
  icon: CardIcon;
  cost: number;
  numbers: number[];
  effect: string;
}

export interface LandmarkInfo {
  name: string;
  cost: number;
  effect: string;
}

export const ICONS: Record<CardIcon, string> = {
  WHEAT: '🌾',
  COW: '🐄',
  BREAD: '🍞',
  CUP: '☕',
  GEAR: '⚙️',
  FACTORY: '🏭',
  FRUIT: '🍎',
  TOWER: '🗼',
};

export const COLOR_NAMES: Record<CardColor, string> = {
  BLUE: 'в ход любого игрока',
  GREEN: 'в свой ход',
  RED: 'в ход соперника',
  PURPLE: 'в свой ход',
};

export const ESTABLISHMENTS: Record<Establishment, EstablishmentInfo> = {
  WHEAT_FIELD: { name: 'Wheat Field', color: 'BLUE', icon: 'WHEAT', cost: 1, numbers: [1], effect: '+1 монета' },
  RANCH: { name: 'Ranch', color: 'BLUE', icon: 'COW', cost: 1, numbers: [2], effect: '+1 монета' },
  BAKERY: { name: 'Bakery', color: 'GREEN', icon: 'BREAD', cost: 1, numbers: [2, 3], effect: '+1 монета' },
  CAFE: { name: 'Cafe', color: 'RED', icon: 'CUP', cost: 2, numbers: [3], effect: 'Забрать 1 у бросившего' },
  CONVENIENCE_STORE: {
    name: 'Convenience Store', color: 'GREEN', icon: 'BREAD', cost: 2, numbers: [4], effect: '+3 монеты',
  },
  FOREST: { name: 'Forest', color: 'BLUE', icon: 'GEAR', cost: 3, numbers: [5], effect: '+1 монета' },
  STADIUM: {
    name: 'Stadium', color: 'PURPLE', icon: 'TOWER', cost: 6, numbers: [6], effect: 'Забрать по 2 у каждого соперника',
  },
  TV_STATION: {
    name: 'TV Station', color: 'PURPLE', icon: 'TOWER', cost: 7, numbers: [6], effect: 'Забрать 5 у выбранного соперника',
  },
  BUSINESS_CENTER: {
    name: 'Business Center', color: 'PURPLE', icon: 'TOWER', cost: 8, numbers: [6],
    effect: 'Обменять своё предприятие на чужое (не фиолетовые)',
  },
  CHEESE_FACTORY: {
    name: 'Cheese Factory', color: 'GREEN', icon: 'FACTORY', cost: 5, numbers: [7], effect: '+3 за каждую 🐄',
  },
  FURNITURE_FACTORY: {
    name: 'Furniture Factory', color: 'GREEN', icon: 'FACTORY', cost: 3, numbers: [8], effect: '+3 за каждую ⚙️',
  },
  MINE: { name: 'Mine', color: 'BLUE', icon: 'GEAR', cost: 6, numbers: [9], effect: '+5 монет' },
  FAMILY_RESTAURANT: {
    name: 'Family Restaurant', color: 'RED', icon: 'CUP', cost: 3, numbers: [9, 10], effect: 'Забрать 2 у бросившего',
  },
  APPLE_ORCHARD: { name: 'Apple Orchard', color: 'BLUE', icon: 'WHEAT', cost: 3, numbers: [10], effect: '+3 монеты' },
  FRUIT_AND_VEGETABLE_MARKET: {
    name: 'Fruit & Vegetable Market', color: 'GREEN', icon: 'FRUIT', cost: 2, numbers: [11, 12],
    effect: '+2 за каждую 🌾',
  },
};

/** Declaration order of core's enum, which is also roughly the order of activation numbers. */
export const ESTABLISHMENT_ORDER = Object.keys(ESTABLISHMENTS) as Establishment[];

export const LANDMARKS: Record<Landmark, LandmarkInfo> = {
  TRAIN_STATION: { name: 'Train Station', cost: 4, effect: 'Можно бросать 2 кубика' },
  SHOPPING_MALL: { name: 'Shopping Mall', cost: 10, effect: '+1 к доходу карт 🍞 и ☕' },
  AMUSEMENT_PARK: { name: 'Amusement Park', cost: 16, effect: 'Дубль — ещё один ход' },
  RADIO_TOWER: { name: 'Radio Tower', cost: 22, effect: 'Раз в ход можно перебросить' },
};

export const LANDMARK_ORDER = Object.keys(LANDMARKS) as Landmark[];

export function isPurple(card: Establishment): boolean {
  return ESTABLISHMENTS[card].color === 'PURPLE';
}

export function numbersLabel(card: Establishment): string {
  const numbers = ESTABLISHMENTS[card].numbers;
  return numbers.length > 1 ? `${numbers[0]}–${numbers[numbers.length - 1]}` : `${numbers[0]}`;
}
