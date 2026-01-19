export interface SofascoreEvent {
  id: number;
  startTimestamp: number;
  homeTeam: Team;
  awayTeam: Team;
  homeScore?: Score;
  awayScore?: Score;
  status: Status;
  tournament: Tournament;
  odds?: Odds;
  voting?: Voting;
  homeFormScore?: number;
  awayFormScore?: number;
  lastUpdated?: number;
}

export interface Team {
  id: number;
  name: string;
  country?: Country;
}

export interface Country {
  name: string;
}

export interface Score {
  current?: number;
  display?: number;
}

export interface Status {
  type: string;
  description: string;
}

export interface Tournament {
  id: number;
  name: string;
  category: Category;
}

export interface Category {
  name: string;
  country?: Country;
}

export interface Odds {
  home?: string;
  draw?: string;
  away?: string;
}

export interface Voting {
  home?: number;
  draw?: number;
  away?: number;
}
