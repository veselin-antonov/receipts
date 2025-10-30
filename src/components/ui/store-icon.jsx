import { Store } from 'lucide-react';

import imgUrl from '@/assets/store-icons.svg';

const StoreIcon = ({ iconId, size = 20 }) => {
  return iconId ? (
    <svg width={size} height={size} viewBox="0 0 48 48">
      <use href={imgUrl + '#' + iconId} />
    </svg>
  ) : (
    <Store size={size} />
  );
};

export default StoreIcon;
