import imgUrl from '@/assets/store-icons.svg';
import { Store } from 'lucide-react';

const StoreIcon = ({ iconId, size = 20 }) => {
  return iconId ? (
    <svg width={size} height={size} viewBox='0 0 48 48'>
      <use href={imgUrl + '#' + iconId} />
    </svg>
  ) : (
    <Store size={size}/>
  );
};

export default StoreIcon;
