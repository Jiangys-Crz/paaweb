import axios from 'axios';
import type { GlobalSearchResult } from '../types/search';

const API_PREFIX = '/api';

export const searchGlobal = async (keyword: string): Promise<GlobalSearchResult> => {
  const response = await axios.get(`${API_PREFIX}/common/search`, {
    params: { keyword }
  });
  return response.data.data;
};